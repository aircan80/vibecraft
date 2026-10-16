"""CLI shim: python make_fixture.py --cache DIR --build B --out OUT --items 1,2,3 ..."""
import sys

from clientdata.fixture import main

if __name__ == "__main__":
    sys.exit(main())
