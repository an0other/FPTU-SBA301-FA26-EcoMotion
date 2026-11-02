"""Batch manual ground-truth annotation, compatible with DocumentAI v0.13."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from PIL import Image, ImageOps, ImageTk
from document_ai.geometry import rectify
from document_ai.reader.corner_order import order_corners


def side_of(path, override=None):
    if override:
        return override
    matches = re.findall(r'(?:^|[-_])(front|back)(?=[-_.]|$)', path.name.lower())
    if len(matches) != 1:
        raise ValueError('Cannot infer FRONT/BACK from filename: ' + path.name)
    return matches[0].upper()


def selection_for(image, raw, points):
    ordered = order_corners(points).tolist()
    selection = {'sha256': hashlib.sha256(raw).hexdigest(),
                 'cornerConvention': 'TOPMOST_CLOCKWISE/v2', 'corners': ordered}
    result = rectify(image, selection, selection['sha256'])
    if result.status != 'PASS':
        raise ValueError(', '.join(result.reasons))
    return selection, result.payload['image']


def valid_label(path, side, image, raw):
    try:
        data = json.loads(path.read_text(encoding='utf-8-sig'))[side]
        if data['sha256'] != hashlib.sha256(raw).hexdigest():
            return False
        return rectify(image, data, data['sha256']).status == 'PASS'
    except (OSError, ValueError, KeyError, TypeError):
        return False


def atomic_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    os.replace(temp, path)


def main():
    import tkinter as tk
    from tkinter import messagebox
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', default='training/annotations')
    parser.add_argument('--side', choices=['FRONT', 'BACK'])
    parser.add_argument('--review', action='store_true', help='Include already labeled images')
    args = parser.parse_args()
    source, output = Path(args.input).resolve(), Path(args.output).resolve()
    if not source.is_dir():
        parser.error('Input directory does not exist')
    files = sorted(p for p in source.rglob('*') if p.suffix.lower() in {'.jpg', '.jpeg', '.png'} and p.is_file())
    if not files:
        parser.error('No JPG/JPEG/PNG images found')
    # Preserve relative directories and original extension to prevent filename collisions.
    queue = [(p, output / (str(p.relative_to(source)) + '.json'), side_of(p, args.side)) for p in files]
    root = tk.Tk()
    root.title('DocumentAI - Batch four-corner labels')
    max_w = min(1200, max(400, root.winfo_screenwidth() - 100))
    max_h = min(780, max(300, root.winfo_screenheight() - 230))
    canvas = tk.Canvas(root, highlightthickness=0)
    canvas.pack()
    info = tk.Label(root, text='', wraplength=max_w)
    info.pack()
    bar = tk.Frame(root)
    bar.pack()
    state = {'index': -1, 'points': [], 'photo': None, 'saved': 0, 'skipped': 0}

    def render():
        canvas.delete('all')
        canvas.create_image(0, 0, image=state['photo'], anchor='nw')
        coords = state['points']
        for i, (x, y) in enumerate(coords):
            canvas.create_oval(x-4, y-4, x+4, y+4, outline='red', width=2)
            canvas.create_text(x+12, y+12, text=str(i+1), fill='red')
        if len(coords) == 4:
            try:
                ordered = order_corners(coords).tolist()
                canvas.create_polygon(*[v for pt in ordered for v in pt], outline='red', fill='', width=2)
            except ValueError:
                pass

    def advance():
        while True:
            state['index'] += 1
            if state['index'] >= len(queue):
                messagebox.showinfo('Finished', f"Saved this session: {state['saved']}\nSkipped manually: {state['skipped']}\nRun again to resume skipped images.")
                root.destroy()
                return
            path, label, side = queue[state['index']]
            try:
                raw = path.read_bytes()
                with Image.open(path) as im:
                    image = ImageOps.exif_transpose(im).convert('RGB')
                if not args.review and valid_label(label, side, image, raw):
                    continue
            except Exception as exc:
                messagebox.showwarning('Cannot open image', f'{path.name}\n{exc}')
                continue
            state.update(path=path, label=label, side=side, raw=raw, image=image, points=[])
            scale = min(1, max_w/image.width, max_h/image.height)
            size = (max(1, round(image.width*scale)), max(1, round(image.height*scale)))
            state['display_size'] = size
            state['photo'] = ImageTk.PhotoImage(image.resize(size))
            canvas.config(width=size[0], height=size[1])
            info.config(text=f"{state['index']+1}/{len(queue)} | {path.name} | {side}\nClick 4 physical card corners in any order. Enter: preview/save. Backspace: undo. S: skip. Esc: exit.")
            render()
            return

    def click(event):
        if len(state['points']) < 4:
            w, h = state['display_size']
            state['points'].append((min(w-1, max(0, event.x)), min(h-1, max(0, event.y))))
            render()

    def undo(event=None):
        if state['points']:
            state['points'].pop()
            render()

    def save(event=None):
        if len(state['points']) != 4:
            messagebox.showinfo('Four corners required', 'Select exactly 4 corners first.')
            return
        image = state['image']
        w, h = state['display_size']
        points = [[x * image.width/w, y * image.height/h] for x,y in state['points']]
        try:
            selection, crop = selection_for(image, state['raw'], points)
        except ValueError as exc:
            messagebox.showerror('Invalid corners', str(exc))
            return
        dialog = tk.Toplevel(root)
        dialog.title('Check crop - orientation is handled later by OCR pipeline')
        ratio = min(1, 850/crop.width, 500/crop.height)
        photo = ImageTk.PhotoImage(crop.resize((max(1, round(crop.width*ratio)), max(1, round(crop.height*ratio)))))
        preview = tk.Label(dialog, image=photo)
        preview.image = photo
        preview.pack()
        tk.Label(dialog, text='Check all card edges are included. Crop may still be sideways/upside-down.').pack()
        def confirm():
            try:
                atomic_json(state['label'], {state['side']: selection})
            except OSError as exc:
                messagebox.showerror('Save failed', str(exc), parent=dialog)
                return
            state['saved'] += 1
            dialog.destroy()
            advance()
        tk.Button(dialog, text='Save and next (Enter)', command=confirm).pack(side='right')
        tk.Button(dialog, text='Adjust corners (Esc)', command=dialog.destroy).pack(side='left')
        dialog.bind('<Return>', lambda e: confirm())
        dialog.bind('<Escape>', lambda e: dialog.destroy())
        dialog.transient(root)
        dialog.grab_set()
        dialog.focus_set()
        root.wait_window(dialog)
        return 'break'

    def skip(event=None):
        state['skipped'] += 1
        advance()

    canvas.bind('<Button-1>', click)
    for text, cmd in [('Undo', undo), ('Clear', lambda: (state.update(points=[]), render())), ('Preview / Save', save), ('Skip', skip), ('Exit', root.destroy)]:
        tk.Button(bar, text=text, command=cmd).pack(side='left', padx=4, pady=5)
    root.bind('<Return>', save)
    root.bind('<BackSpace>', undo)
    root.bind('<s>', skip)
    root.bind('<Escape>', lambda e: root.destroy())
    advance()
    root.mainloop()


if __name__ == '__main__':
    main()
