"""CLI shim: python convert.py --cache DIR --build B --out DIR"""
import sys

from clientdata.convert import main

if __name__ == "__main__":
    sys.exit(main())
