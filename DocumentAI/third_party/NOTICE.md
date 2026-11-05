# Attribution

VietOCR 0.3.13: https://github.com/pbcquoc/vietocr — pbcquoc and contributors.

The experimental device decoder is an adaptation of the public greedy decoding algorithm in `vietocr/tool/translate.py`. Changes: retain token history on the inference device, process the last timestep distribution, transfer final arrays once, and report truncation explicitly. Confidence/EOS behavior is compared against reference semantics in CPU tests. Existing upstream predictor remains the reference path.

The license file bundled with the inspected 0.3.13 wheel is retained as `vietocr-0.3.13-LICENSE.txt`. The wheel's package classifier separately says MIT; this release does not resolve that metadata inconsistency. No VietOCR model weights or full upstream package are redistributed here.

Rust normalization, experiment orchestration and profile/fallback changes are part of this project. Rust crate has no external dependencies.
