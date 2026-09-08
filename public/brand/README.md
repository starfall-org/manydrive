# ManyDrive identity

The folded M connects two storage pillars in mint and blue on a midnight background.

- `manydrive-logo.svg`: primary icon with rounded background.
- `manydrive-mark.svg`: transparent mark for dark backgrounds.
- `manydrive-logo.png`: 1024 × 1024 export.

Source geometry and palette live in `scripts/generate_brand.py`. Install CairoSVG in a Python environment, then run `python scripts/generate_brand.py` from the repository to regenerate the web assets, all five Android PNG densities, adaptive icons, and Android 13 monochrome icon. Android controls the adaptive icon's outer shape.
