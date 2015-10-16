#  demo Provider-Host High-Trust Apps of sharepoint(On Premise) on Non-Microsoft Platforms implement with java

   Provider-Host High-Trust App of sharepoint(On Premise) on non-Microsoft platforms will need to manage [JWT Tokens](http://openid.net/specs/draft-jones-json-web-token-07.html), [MS-SPS2SAUTH](http://msdn.microsoft.com/en-us/library/hh631177(v=office.12).aspx), Windows  user’s SID,  X.509 Certificate signature etc. in order to form the access token.
   thanks to  [Kirk Evans' blog: High Trust SharePoint Apps on Non-Microsoft Platforms](http://blogs.msdn.com/b/kaevans/archive/2014/07/14/high-trust-sharepoint-apps-on-non-microsoft-platforms.aspx), the work is easy now.
      
   use spring boot to qucikly building app, but not depend spring .
   
   use [jjwt](https://github.com/jwtk/jjwt) to creating and verifying JSON Web Tokens (JWTs) , it's 0.6 release can not find in center maven repo now ,but u should build youself.
   

## run this app:

### First:Configuring High-Trust On-Premises
   
- [How to: Create high-trust apps for SharePoint 2013 (advanced topic)](https://msdn.microsoft.com/en-us/library/office/fp179901(v=office.15).aspx)

### Second:  According to the tutorial , Create a high-trust SharePoint Add-in and deploy and test successful.

### Last: config this demo app:

- copy HighTrustSampleCert.pfx file to src/main/resources , and use keytool command import to HightTrust.keystore
  
    `keytool -importkeystore -srckeystore HighTrustSampleCert_password.pfx -srcstoretype pkcs12 -destkeystore HightTrust.keystore -deststoretype JKS`
    
    provide  the param -alias ,or let  keytool generate and output alias, then copy alias to "keyAliasName" in  GreetingController.java.
    
- config appliation.properties: server.por, server.ssl.key-store etc. 

- config GreetingController.java: targetApplicationUri, ClientId, IssuerId, KeyPassword, SharePointPrincipal testSID etc.

   testSID is a user sid obtained from a windows user which can login to sharepoint,  obtain it from windows command `wmic useraccount where name='administrator' get sid` on  sharepoint server
     
- mvn clean spring-boot:run

- from sharepoint , click the hightrust sample link to test. 


### todo:
  
  - user sid is hardcode now, need get user profice from AD, or [ Using SharePoint Apps with SAML and FBA Sites in SharePoint 2013](http://blogs.technet.com/b/speschka/archive/2012/12/07/using-sharepoint-apps-with-saml-and-fba-sites-in-sharepoint-2013.aspx)
  
  - make a standalone lib or intergrate with spring security
    
    
   