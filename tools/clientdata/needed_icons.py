"""CLI shim: python needed_icons.py --db DB --cache DIR --build BUILD"""
import sys

from clientdata.needed_icons import main

if __name__ == "__main__":
    sys.exit(main())
