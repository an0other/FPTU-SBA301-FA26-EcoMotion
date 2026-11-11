from pathlib import Path

import numpy as np

from identity_verification.run_detector import prepare_image
from identity_verification.scrfd_detection import get_detector_session


def main():
    tensor = prepare_image(Path("inputs/cccd-front.jpeg"))

    cached_session = get_detector_session()

    first = cached_session.run(
        None,
        {cached_session.get_inputs()[0].name: tensor},
    )

    second = cached_session.run(
        None,
        {cached_session.get_inputs()[0].name: tensor},
    )

    fresh_session = get_detector_session.__wrapped__()

    fresh = fresh_session.run(
        None,
        {fresh_session.get_inputs()[0].name: tensor},
    )

    repeated_equal = all(
        np.allclose(a, b, rtol=1e-5, atol=1e-6)
        for a, b in zip(first, second)
    )

    fresh_equal = all(
        np.allclose(a, b, rtol=1e-5, atol=1e-6)
        for a, b in zip(first, fresh)
    )

    print("Repeated inference consistent:", repeated_equal)
    print("Cached vs fresh consistent:", fresh_equal)


if __name__ == "__main__":
    main()