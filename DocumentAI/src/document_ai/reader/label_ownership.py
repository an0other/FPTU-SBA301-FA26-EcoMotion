"""Printed label boundaries; never mistake a name containing Hằng for Hạng."""
import re
from document_ai.name_comparison import unaccent_name


def label_keys(text,aliases):
    flat=unaccent_name(text)
    result=set()
    for key,values in aliases.items():
        for alias in values:
            end=r'(?![A-Z0-9])' if alias[-1].isalnum() else ''
            if re.search(r'(?:^|[/|])\s*'+re.escape(alias)+end,flat):
                result.add(key);break
    return result
