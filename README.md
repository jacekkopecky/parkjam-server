ParkJam back-end server and admin console
==============

Web site: http://parking.kmi.open.ac.uk 

Copyright: 
 - 2012-2013 Jacek Kopecky (jacek@jacek.cz) 
 - 2012-2013 Knowledge Media Institute, The Open University, Milton Keynes, UK
   
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.


---------------------------------
Sources

The back-end server is configured in two web apps:

ParkJam-server (directory "server"): the public-facing API
    this is deployed as a public service

ParkJam-admin  (directory "admin"):  the private administration console
    this is only available from a trusted network (localhost); 
    beyond needing access to the server, there is no authentication in the admin console

I'll be happy to provide installation instructions if requested -- Jacek


---------------------------------
Libraries and Licenses


ParkJam server uses the following libraries; with licenses available in the "licenses" directory

Apache Jakarta Commons Codec
  licensed under Apache Software License 1.1

Apache Commons Lang
Apache Commons Logging
Apache Jakarta HttpClient
  licensed under Apache License 2.0

Jersey (https://jersey.java.net/) 
  licensed under CDDL 1.1 and GPL 2 with CPE

Jersey uses ASM, license covered in ./licenses/Jersey-third-party-license-readme.txt

JUnit
  licensed under CPL (Common Public License) v1.0

OpenRDF Sesame
  license in ./licenses/sesame-license.txt

RDF2Go
  BSD license in ./licenses/rdf2go-license.txt

SLF4J
  MIT license in ./licenses/slf4j-license.txt

Mindrot.org BCrypt
  license in source file
