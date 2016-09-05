# folkets db generator

Takes dictionary xml files from folkets lexicon and makes a denormalised db containing them

### Attribution
 
This project build upon the lexicons available from [Folkets lexicon](http://folkets-lexikon.csc.kth.se/folkets/om.html). 

The Folkets lexicon is distributed under the Distributed Creative Commons Attribution-Share Alike 2.5 Generic license. The denormalised databases available from this repository's [releases](https://github.com/baz8080/folkets_db_generator/releases) are distributed under the same licence, and the code is distributed under the Apache 2.0 licence.

### Related projects

1. [Folkets Android app](https://github.com/baz8080/folkets_android)

### Running the project

1. Clone
2. Build `./gradlew clean createDatabases`
3. Database is created in `build/folkets.sqlite`

### Word types

pp: preposition
nn: noun
ab: adverb
jj: adjective
abbrev: abbreviation
pn: pronoun
vb: verb
in: interjection
rg: cardinal number
prefix: prefix
kn: conjunction
ie: infinitival marker
article: article
pm: proper noun
suffix: suffix
hp: prounoun (determiner)
ps: possessive prounoun
sn: subordinating conjunction
