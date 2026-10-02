"""Compatibility entry point: rebuild the complete public guide set."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name('generate_guides.py')), run_name='__main__')
