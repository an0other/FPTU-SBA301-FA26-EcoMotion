"""Kiểm tra quy tắc phân tầng của backend EcoMotion.

Quy tắc:
  1. Controller không inject Repository / EntityManager / JdbcTemplate (chỉ gọi service).
  2. Service chỉ dùng Repository của CHÍNH module mình (module = thư mục ngay dưới com/fptufa26/ecomotion).
     Muốn dữ liệu module khác thì gọi service của module đó.
  3. Service không "lách" bằng EntityManager để truy vấn / find entity của module khác.

Thư mục seed/ (dữ liệu dev) và common/config (vá schema lúc khởi động) được miễn.

Chạy:  python scripts/check-layering.py      (exit code 1 nếu có vi phạm)
"""
import os
import re
import sys

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'Backend', 'EcoMotion', 'src', 'main', 'java', 'com', 'fptufa26', 'ecomotion')
EXEMPT_MODULES = {'seed'}


def module_of(path):
    return os.path.relpath(path, ROOT).split(os.sep)[0]


def java_files():
    for d, _, files in os.walk(ROOT):
        for f in files:
            if f.endswith('.java'):
                yield os.path.join(d, f)


repo_module, entity_module = {}, {}
for p in java_files():
    parts = os.path.relpath(p, ROOT).split(os.sep)
    name = os.path.basename(p)[:-5]
    if 'repository' in parts and name.endswith('Repository'):
        repo_module[name] = parts[0]
    if 'entity' in parts:
        entity_module[name] = parts[0]

violations = []
for p in java_files():
    rel = os.path.relpath(p, ROOT)
    mod = module_of(p)
    parts = rel.split(os.sep)
    if mod in EXEMPT_MODULES or 'repository' in parts or 'config' in parts:
        continue
    src = open(p, encoding='utf-8', errors='ignore').read()

    if rel.endswith('Controller.java'):
        for m in re.finditer(r'private final\s+(?:[\w.<>]+\.)?(\w*(?:Repository|EntityManager|JdbcTemplate))\b', src):
            violations.append(f'{rel}: controller dùng trực tiếp {m.group(1)}')
        continue

    if '@Service' not in src and '@Component' not in src:
        continue
    for m in re.finditer(r'(?:private final\s+|ObjectProvider<)(?:[\w.]+\.)?(\w+Repository)\b', src):
        owner = repo_module.get(m.group(1))
        if owner and owner != mod:
            violations.append(f'{rel}: dùng {m.group(1)} của module {owner}')
    if 'EntityManager' in src:
        seen = set()
        for pattern in (r'(?:from|join)\s+(\w+)\s',
                        r'entityManager\.(?:find|getReference)\(\s*(?:[\w.]+\.)?(\w+)\.class'):
            for m in re.finditer(pattern, src):
                owner = entity_module.get(m.group(1))
                if owner and owner != mod and m.group(1) not in seen:
                    seen.add(m.group(1))
                    violations.append(f'{rel}: truy vấn entity {m.group(1)} của module {owner} qua EntityManager')

if violations:
    print('Vi phạm phân tầng:')
    for v in sorted(set(violations)):
        print('  -', v)
    sys.exit(1)
print('OK: không có vi phạm phân tầng (controller -> service -> repository cùng module).')
