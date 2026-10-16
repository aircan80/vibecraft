"""CLI shim: python fetch.py --product wow_classic_beta --cache DIR ..."""
import sys

from clientdata.fetch import main

if __name__ == "__main__":
    sys.exit(main())
