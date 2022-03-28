## 1.2.1 2022-03-28

 * [MODCPCT-64](https://issues.folio.org/browse/MODCPCT-64) One-record OCLC (Create) Data Import takes over 9 seconds

## 1.2.0 2022-02-18

Features:

 * [MODCPCT-60](https://issues.folio.org/browse/MODCPCT-60) Allow encoding of imported records to be given.
This is specified as property `marcencoding` in [copycat profile](ramls/copycatprofile.json).
 * [MODCPCT-63](https://issues.folio.org/browse/MODCPCT-63) Log time it takes to do Z39.50 search and fetch

Fixes:

 * [MODCPCT-59](https://issues.folio.org/browse/MODCPCT-59) Two mod-copycat permissions share same displayName
 * [MODCPCT-61](https://issues.folio.org/browse/MODCPCT-61) Update to RMB 33.2.5

## 1.1.0 2021-06-18

 * [MODCPCT-45](https://issues.folio.org/browse/MODCPCT-45) Upgrade to Vert.x 4.1.0 / RMB 33.0.1
 * [MOCCPCT-44](https://issues.folio.org/browse/MOCCPCT-44) Update interface changes for source-manager

## 1.0.4 2021-06-14

 * [MODCPCT-39](https://issues.folio.org/browse/MODCPCT-39) Z39.50 auth errors are handled with more informative message.
   Context for SRS interaction is reported in case of errors.
 * [MODCPCT-48](https://issues.folio.org/browse/MODCPCT-48) No polling for overlay/update, but wait instead
 * [MODCPCT-40](https://issues.folio.org/browse/MODCPCT-40) "Getting started guide" has outdated material

## 1.0.3 2021-04-23

Changes:

 * [MOCCPCT-35](https://issues.folio.org/browse/MOCCPCT-35) Poll failure: omit internalIdentifier in response
 * [MODCPCT-37](https://issues.folio.org/browse/MODCPCT-37) Make OCLC Worldcat return records in UTF-8

Fixes:

 * [MODCPCT-34](https://issues.folio.org/browse/MODCPCT-34) copycat assumes MARC8 encoding with raw record
 * [MODCPCT-32](https://issues.folio.org/browse/MODCPCT-32) Ignore errors for polling storage

## 1.0.2 2021-04-22

Fixes:

 * [MODCPCT-31](https://issues.folio.org/browse/MODCPCT-31) Import no longer returns instance Id

Other:

 * [MODCPCT-28](https://issues.folio.org/browse/MODCPCT-28) Update maven.indexdata.com url
 * [MODCPCT-27](https://issues.folio.org/browse/MODCPCT-27) Omit jobProfileInfo for execution

## 1.0.1 2021-04-07

 * [MODCPCT-26](https://issues.folio.org/browse/MODCPCT-26) Missing permission source-storage.sourceRecords.get

 * [MODCPCT-22](https://issues.folio.org/browse/MODCPCT-22) Do not set the data import profile name

## 1.0.0 2021-03-19

First release of mod-copycat.


