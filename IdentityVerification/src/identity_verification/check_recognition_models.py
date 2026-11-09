from pathlib import Path

import onnxruntime as ort


def main():
    model_root = Path(__file__).resolve().parents[2] / "models" / "buffalo_sc"

    for filename in ("det_500m.onnx", "w600k_mbf.onnx"):
        matches = list(model_root.rglob(filename))

        if len(matches) != 1:
            raise RuntimeError(
                f"Expected exactly one {filename}, found {len(matches)}"
            )

        session = ort.InferenceSession(
            str(matches[0]),
            providers=["CPUExecutionProvider"],
        )

        print(f"\nModel: {filename}")
        print("Providers:", session.get_providers())

        for item in session.get_inputs():
            print("Input:", item.name, item.shape, item.type)

        for item in session.get_outputs():
            print("Output:", item.name, item.shape, item.type)


if __name__ == "__main__":
    main()