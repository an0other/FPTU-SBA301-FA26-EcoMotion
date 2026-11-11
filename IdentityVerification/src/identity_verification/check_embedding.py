from pathlib import Path

import numpy as np
from PIL import Image

from identity_verification.face_matching import (
    create_session,
    extract_embedding,
    extract_embedding_from_image,
)


def main():
    path = Path("output/selfie_aligned.png")
    session = create_session()

    first = extract_embedding(session, path)

    with Image.open(path) as source:
        image = source.convert("RGB")

    second = extract_embedding_from_image(session, image)

    print("Same session:", session is create_session())
    print("Embedding shape:", second.shape)
    print("Embedding length:", float(np.linalg.norm(second)))
    print(
        "Path result matches memory result:",
        np.allclose(first, second, rtol=1e-5, atol=1e-6),
    )


if __name__ == "__main__":
    main()