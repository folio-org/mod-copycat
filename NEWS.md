## 1.8.0 2025-03-10

* [MODCPCT-91](https://folio-org.atlassian.net/browse/MODCPCT-91) Java 21.

## 1.7.0 2024-10-17

* [MODCPCT-87](https://folio-org.atlassian.net/browse/MODCPCT-87) Update `source-manager-job-executions` to `3.4` and `source-storage-source-records `to `3.5`

## 1.6.0 2024-03-21

* [MOCCPCT-82](https://issues.folio.org/browse/MOCCPCT-82) Upgrade to Vert.x 4.5.4
* [MOCCPCT-80](https://issues.folio.org/browse/MOCCPCT-80) Upgrade to RMB 35.2.0
* [MOCCPCT-81](https://issues.folio.org/browse/MOCCPCT-81) Finalize upgrade to Java 17

## 1.5.0 2023-10-11

* [MOCCPCT-76](https://issues.folio.org/browse/MOCCPCT-76) Upgrade mod-copycat to Java 17

## 1.4.0 2023-20-16

 * [MOCCPCT-74](https://issues.folio.org/browse/MOCCPCT-74) Upgrade to RMB 35.0.6, Vert.x 4.3.8
 * [MODCPCT-69](https://issues.folio.org/browse/MODCPCT-69) Create an upgrade process for existing Z39.50 target profiles
 * [MODCPCT-71](https://issues.folio.org/browse/MODCPCT-71) Allow importing single records with selected jobProfile

## 1.3.1 2022-10-28

 * [MODCPCT-68](https://issues.folio.org/browse/MODCPCT-68) Upgrade to RMB 35, Vert.x 4.3.4

## 1.3.0 2022-06-30

 * [MODCPCT-66](https://issues.folio.org/brose/MODCPCT-66) Upgrade to RMB 34, Vert.x 4.3.1

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


