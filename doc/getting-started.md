# Building, running, initializing and using mod-copycat

Mike Taylor, Index Data ApS.
mike@indexdata.com

16 December 2020.

<!-- md2toc -l 2 getting-started.md -->
* [To build](#to-build)
* [To run](#to-run)
* [To initialize](#to-initialize)
* [To use](#to-use)
* [Integrating into a FOLIO-backend Vagrant box](#integrating-into-a-folio-backend-vagrant-box)
* [To unassociate, undeploy and remove a running module](#to-unassociate-undeploy-and-remove-a-running-module)
* [Adding permissions on the UI side](#adding-permissions-on-the-ui-side)


## To build

You need
[yaz4j](https://github.com/indexdata/yaz4j)
and
[yaz](https://www.indexdata.com/yaz)
before you can compile and install mod-copycat.

	term1$ sudo apt install yaz
	term1$ git clone https://github.com/indexdata/yaz4j.git
	term1$ git checkout d7cd6967d297c92c179d9896b0150f7509f789f8
	term1$ cd yaz4j

The install below whould install `yaz4j-1.6-SNAPSHOT.jar` in your Maven repository.

	term1$ mvn -B install

This also produces a native DLL/shared object file.. This needs to be available when
using the yaz4j jar. Google for `java.library.path` to see where it could be
installed on your platform. On Ubuntu/Debian amd64 architecture, it suffices to
copy like this:

	term1$ sudo cp unix/target/libyaz4j.so /usr/lib/x86_64-linux-gnu/

## To build mod-copycat

	term1$ cd mod-copycat
	term1$ mvn install

## To run

	term1$ java -jar target/mod-copycat-fat.jar

## To initialize

In another terminal:

	term2$ curl -d'{"module_to":"foo"}' -HX-Okapi-Tenant:testlib "-HAccept:*/*" -HContent-Type:application/json http://localhost:8081/_/tenant
	[]
	term2$ 

While this is happening, expect to see worrying warnings on the server side:

	WARNING: An illegal reflective access operation has occurred
	WARNING: Illegal reflective access by org.postgresql.jdbc.TimestampUtils (file:/Users/mike/git/folio/other/mod-copycat/target/mod-copycat-fat.jar) to field java.util.TimeZone.defaultTimeZone
	WARNING: Please consider reporting this to the maintainers of org.postgresql.jdbc.TimestampUtils
	WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
	WARNING: All illegal access operations will be denied in a future release

Apparently this is nothing to worry about.

## To use

Create a target profile:

	term2$ curl  -d'{"url":"z3950.indexdata.com/marc", "name":"ID MARC test"}' -HX-Okapi-Tenant:testlib "-HAccept:*/*" -HContent-Type:application/json http://localhost:8081/copycat/target-profiles
	{
	  "id" : "6a69ab96-51af-4827-bb7d-7a9cf2f1a8cf",
	  "name" : "ID MARC test",
	  "url" : "z3950.indexdata.com/marc"
	}
	term$

List all target profiles:

	term2$ curl   -HX-Okapi-Tenant:testlib "-HAccept:*/*" -HContent-Type:application/json http://localhost:8081/copycat/target-profiles
	{
	  "targetprofiles" : [ {
	    "id" : "6a69ab96-51af-4827-bb7d-7a9cf2f1a8cf",
	    "name" : "ID MARC test",
	    "url" : "z3950.indexdata.com/marc"
	  } ],
	  "totalRecords" : 1
	}
	term2$ 

Import a record based on an external identifier (OCLC number):

	term2$ curl -d'{"externalIdentifier":"isbn123", "targetProfileId":"1d0489ab-3989-4f0b-b535-725bebf21373"}'   -HX-Okapi-Tenant:testlib "-HAccept:*/*" -HContent-Type:application/json http://localhost:8081/copycat/imports
	Not implemented
	term2$ 

## Integrating into a FOLIO-backend Vagrant box

Create the configuration file for a FOLIO core backend VM:

	$ vagrant init folio/snapshot-backend-core

Now start the VM for the first time. This will take some time, as it will need to download the VM image:

	$ vagrant up

Once the VM is running, there are several approaches that can be taken:
* [Use a "host-only" private network in VirtualBox](https://github.com/folio-org/folio-ansible/blob/master/doc/index.md#running-backend-modules-on-your-host-system) to enable Okapi on the VM to contact mod-copycat on the host.
* [Use an SSH tunnel](https://github.com/folio-org/mod-graphql/blob/master/doc/developing-with-a-vagrant-box.md#b-run-mod-graphql-in-the-host-box) to enable Okapi on the VM to contact mod-copycat on the host.
* [Run mod-copycat inside the Vagrant box](https://github.com/folio-org/mod-graphql/blob/master/doc/developing-with-a-vagrant-box.md#a-run-mod-graphql-inside-the-vagrant-box). The linked document talks about running mod-graphql in this way, but the approach is the same. Note that if you use this approach, then the instance of the module running inside the VM must be initialised and populated: it does not share the database with any instance running in the host machine.

Whichever of these approaches you use, you now need to tell Okapi about the copycat module: load the module, deploy it, and associated it with the `diku` tenant:

	curl -w '\n' -d @target/ModuleDescriptor.json http://localhost:9130/_/proxy/modules
	curl -w '\n' -d @target/DeploymentDescriptor.VM.json http://localhost:9130/_/discovery/modules
	curl -w '\n' -d '[{ "action": "enable", "id": "mod-copycat-1.0.0-SNAPSHOT" }]' http://localhost:9130/_/proxy/tenants/diku/install

Now login and you will be able to access mod-copycat via Okapi. Use the `X-Okapi-Token` from the response headers echoed by the `-D -` option to the login operation:

	curl -w '\n' -D - -X POST -d '{"username":"diku_admin","password":"********"}' -H "Content-type: application/json" -H "Accept: application/json" -H "x-okapi-tenant: diku" http://localhost:9130/authn/login
	curl -w '\n' -H "X-Okapi-Tenant: diku" -H "X-Okapi-Token: XXXXXXXX" -H "Accept: */*" -H "Content-Type: application/json" http://localhost:9130/copycat/target-profiles

## To unassociate, undeploy and remove a running module

This may be necessary so that the module can be re-added in order to force new or changed permissions to be recognised. The process has several steps, since the module must first be unassociated from the tenant and then undeployed before it can be removed:

	curl http://localhost:9130/_/proxy/tenants/diku/modules | grep mod-copycat
	# From the output, extract the module-ID for the next step
	curl -X DELETE http://localhost:9130/_/proxy/tenants/diku/modules/mod-copycat-1.0.0-SNAPSHOT
	curl http://localhost:9130/_/discovery/modules | grep -i copycat
	# From the output, find the deployment UUID for the next step
	curl -X DELETE http://localhost:9130/_/discovery/modules/mod-copycat-1.0.0-SNAPSHOT/15272446-17ba-41b8-b78b-b67df95bbeef
	curl -X DELETE http://localhost:9130/_/proxy/modules/mod-copycat-1.0.0-SNAPSHOT

## Adding permissions on the UI side

This is most easily done using the UI module's `yarn install`ed the Stripes CLI. Updating the module descriptor is straightforward, but it is then necessary to disable and re-enable the module for a tenant before that tenant can see the new permission definitions. Frustratingly, at the time of writing a bug in `stripes` means that it does not presently cache the Okapi URL or tenant name, so these need to be repeatedly provided on the command line:

	yarn stripes mod update --okapi http://localhost:9130
	yarn stripes mod disable --okapi http://localhost:9130 --tenant diku
	yarn stripes mod enable --okapi http://localhost:9130 --tenant diku
	echo ui-inventory.settings.single-record-import | yarn stripes perm assign --user diku_admin --okapi http://localhost:9130 --tenant diku

