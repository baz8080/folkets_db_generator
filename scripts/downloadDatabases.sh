#!/usr/bin/env bash

wget --directory-prefix=src/main/resources --timestamping http://folkets-lexikon.csc.kth.se/folkets/folkets_en_sv_public.xml
wget --directory-prefix=src/main/resources --timestamping http://folkets-lexikon.csc.kth.se/folkets/folkets_sv_en_public.xml