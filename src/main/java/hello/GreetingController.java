package hello;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.TextCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;

@Controller
public class GreetingController {

    URI targetApplicationUri;
    {
        try {
            targetApplicationUri = new URI("http://sharepoint/sites/dev");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }


    private String ClientId = "4ebb8f86-b40c-4cc5-8255-4ebeea018dc5";
    private String IssuerId = "11111111-1111-1111-1111-111111111111";
    //key store password
    private String KeyPassword = "password";
    public  String SharePointPrincipal = "00000003-0000-0ff1-ce00-000000000000";

    private String testSID = "S-1-5-21-1030104071-1452137555-3129204420-500";

    private String keyAliasName = "112e6c9a-d7f3-4c76-bd55-33b270ed67ce";

    public   int HighTrustAccessTokenLifetime = 12;

    private PrivateKey x5tPrivateKey;
    private String x5tThumbPrint;
    private SignatureAlgorithm signatureAlgorithm;
    private  String Realm ;

    public GreetingController(){
        //The JWT signature algorithm we will be using to sign the token
        signatureAlgorithm = SignatureAlgorithm.RS256;
        getX509PrivateKey();
    }

    @RequestMapping(value = "/Pages/Default.aspx", method = RequestMethod.POST)
    public String Default(@RequestParam(value="SPHostUrl", required=true, defaultValue="http://sharepoint/sites/dev") String SPHostUrl,
                          @RequestParam(value="SPLanguage", required=true, defaultValue="zh-CN") String SPLanguage,
                          @RequestParam(value="SPClientTag", required=true, defaultValue="0") String SPClientTag,
                          @RequestParam(value="SPProductNumber", required=false, defaultValue="15.0.4569.1000") String SPProductNumber,
                          Model model) {
        model.addAttribute("SPHostUrl", SPHostUrl);
        model.addAttribute("SPLanguage", SPLanguage);
        model.addAttribute("SPClientTag", SPClientTag);
        model.addAttribute("SPProductNumber", SPProductNumber);

        WindowsIdentity identity = new WindowsIdentity();
        String access_token = GetS2SAccessTokenWithWindowsIdentity(targetApplicationUri, identity);

        // access sharepoint REST Api with "out token"
        Object result = GetSPRestAPI(targetApplicationUri, access_token);
        model.addAttribute("listsResult", result.toString());
        System.out.println(result);

        return "greeting";
    }

    public  Object GetSPRestAPI(URI targetApplicationUri, String access_token)
    {
        try
        {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer "+access_token);
            headers.add("Accept", "application/json;odata=verbose");
            RestTemplate restTemplate = new RestTemplate();
            return restTemplate.execute(targetApplicationUri + "/_api/Web/lists", HttpMethod.GET,
                    new spRequestCallback(null, headers),
                    new HttpMessageConverterExtractor(HashMap.class, restTemplate.getMessageConverters()));

        }
        catch (HttpStatusCodeException e)
        {

            System.out.println(e.getMessage());
            if (e.getResponseBodyAsString() == null)
            {
                return null;
            }
        }
        return null;
    }


    public  String GetS2SAccessTokenWithWindowsIdentity(URI targetApplicationUri,WindowsIdentity identity)
    {
        String realm = StringUtils.isEmpty(Realm) ? GetRealmFromTargetUrl(targetApplicationUri) : Realm;

        Map<String, Object>  claims = identity != null ? GetClaimsWithWindowsIdentity(identity) : null;

        return GetS2SAccessTokenWithClaims(targetApplicationUri.getAuthority(), realm, claims);
    }

    private  Map<String, Object>  GetClaimsWithWindowsIdentity(WindowsIdentity identity)
    {
        Map<String, Object>  claims = new HashMap<>();
        claims.put(ReservedClaims.NameIdentifier, testSID.toLowerCase());
        claims.put("nii", "urn:office:idp:activedirectory");

        return claims;
    }


    public  String GetRealmFromTargetUrl(URI targetApplicationUri)
    {
        try
        {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization: Bearer ", null);
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.execute(targetApplicationUri + "/_vti_bin/client.svc", HttpMethod.POST,
                    new spRequestCallback(null, headers), null);

        }
        catch (HttpStatusCodeException e)
        {

            if (e.getResponseBodyAsString() == null)
            {
                return null;
            }

            String bearerResponseHeader = e.getResponseHeaders().get("WWW-Authenticate").toString();
            if (StringUtils.isEmpty(bearerResponseHeader))
            {
                return null;
            }

            String bearer = "Bearer realm=\"";
            int bearerIndex = bearerResponseHeader.indexOf(bearer);
            if (bearerIndex < 0)
            {
                return null;
            }

            int realmIndex = bearerIndex + bearer.length();

            if (bearerResponseHeader.length() >= realmIndex + 36)
            {

                return bearerResponseHeader.substring(realmIndex, realmIndex + 36);
            }
        }
        return null;
    }

    private  String GetS2SAccessTokenWithClaims(String targetApplicationHostName,String targetRealm,Map<String, Object>  claims) {
        return IssueToken(ClientId, IssuerId, targetRealm, SharePointPrincipal, targetRealm, targetApplicationHostName, true,
                claims,
                claims == null);
    }

    private  String IssueToken(String sourceApplication,
                                String issuerApplication,
                                String sourceRealm,
                                String targetApplication,
                                String targetRealm,
                                String targetApplicationHostName,
                                boolean trustedForDelegation,
                                Map<String, Object>  claims,
                                boolean appOnly) {

        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis +  HighTrustAccessTokenLifetime * 60 * 60 * 1000;


        //----actor token----

        String issuer = StringUtils.isEmpty(sourceRealm) ? issuerApplication : String.format("%s@%s", issuerApplication, sourceRealm);
        String nameid = StringUtils.isEmpty(sourceRealm) ? sourceApplication : String.format("%s@%s", sourceApplication, sourceRealm);
        String audience = String.format("%s/%s@%s", targetApplication, targetApplicationHostName, targetRealm);

        Map<String, Object> actorClaims = new HashMap<>();

        actorClaims.put(ReservedClaims.NameIdentifier, nameid);
        if (trustedForDelegation && !appOnly) {
            actorClaims.put(ReservedClaims.TrustedForImpersonationClaimType, "true");
        }


        String actorTokenString = createActorTokenJWT(null, issuer, audience, nowMillis, expMillis, actorClaims,HighTrustAccessTokenLifetime * 60 * 60 * 1000);

        if (appOnly)
        {
            return actorTokenString;
        }

        //out token

        Map<String, Object>  outerClaims = new HashMap<>();
        outerClaims.putAll(claims);
        outerClaims.put(ReservedClaims.ActorToken, actorTokenString);

        return createOutTokenJWT(null, nameid, audience, nowMillis, expMillis, outerClaims, HighTrustAccessTokenLifetime * 60 * 60 * 1000);
    }


    private  class WindowsIdentity {
        public WindowsIdentity(){
            //todo: get AD user's sid
            // For Kerberos authentication:
            // Credentials credentials =
            //    new UsernamePasswordCredentials(user, pass);
//            NTCredentials credentials = new NTCredentials("Administrator", "~!@Heluo", "tigerMac", "SHAREPOINT");
        }
    }

    private class spRequestCallback implements RequestCallback {

        private final MultiValueMap<String, String> form;

        private final HttpHeaders headers;

        private spRequestCallback(MultiValueMap<String, String> form, HttpHeaders headers) {
            this.form = form;
            this.headers = headers;
        }

        public void doWithRequest(ClientHttpRequest request) throws IOException {
            request.getHeaders().putAll(this.headers);
//            request.getHeaders().setAccept(
//                    Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED));
            //logger.debug("Encoding and sending form: " + form);
            //FORM_MESSAGE_CONVERTER.write(this.form, MediaType.APPLICATION_FORM_URLENCODED, request);
        }
    }


    private class spResponseExtractor implements ResponseExtractor {
        @Override
        public Object extractData(ClientHttpResponse response) throws IOException {
            System.out.println(response.getBody());
            return null;
        }
    }

    //构建 JWT
    private String createActorTokenJWT(String id, String issuer, String audience, long strvalidFrom, long strvalidTo, Map<String, Object> claims,  long ttlMillis) {
        /** ActorToken JWT Json sapmle
        * {
             {"typ":"JWT","alg":"RS256","x5t":"LRxHRIp-BKD7xG7-ktKmgoNT7Eo"}.
            {"aud":"00000003-0000-0ff1-ce00-000000000000/sharepoint@200a8e79-a98e-4b79-a6e3-c637c6482471",
            "iss":"11111111-1111-1111-1111-111111111111@200a8e79-a98e-4b79-a6e3-c637c6482471",
            "nbf":"1444780800",
            "exp":"1444824000",
            "nameid":"4ebb8f86-b40c-4cc5-8255-4ebeea018dc5@200a8e79-a98e-4b79-a6e3-c637c6482471",
            "trustedfordelegation":"true"}}
        * */
        Map<String, Object> headers = new HashMap<>();
        headers.put(ReservedClaims.TypeExpression, ReservedClaims.JsonWebToken);
        headers.put(ReservedClaims.X5t, x5tThumbPrint);

        claims.put(ReservedClaims.Audience, audience);
        claims.put(ReservedClaims.Issuer, issuer);
        claims.put(ReservedClaims.NotBefore, String.valueOf(strvalidFrom/1000));
        claims.put(ReservedClaims.ExpiresOn, String.valueOf(strvalidTo/1000));
        JwtBuilder builder = Jwts.builder().setId(id)
                .setClaims(claims)
                .setHeader(headers)
                .signWith(signatureAlgorithm, x5tPrivateKey);

        return builder.compact();
    }

    //构建 JWT
    private String createOutTokenJWT(String id, String issuer, String audience, long strvalidFrom, long strvalidTo, Map<String, Object> claims,  long ttlMillis) {
        /**
         *  Out Token JWT Json sapmle。
         {"typ":"JWT","alg":"none"}.
         {"aud":"00000003-0000-0ff1-ce00-000000000000/sharepoint@200a8e79-a98e-4b79-a6e3-c637c6482471",
         "iss":"4ebb8f86-b40c-4cc5-8255-4ebeea018dc5@200a8e79-a98e-4b79-a6e3-c637c6482471",
         "nbf":"1444889107",
         "exp":"1444932307",
         "nameid":"s-1-5-21-1030104071-1452137555-3129204420-500",
         "nii":"urn:office:idp:activedirectory",
         "actortoken":"eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6IkxSeEhSSXAtQktEN3hHNy1rdEttZ29OVDdFbyJ9.eyJhdWQiOiIwMDAwMDAwMy0wMDAwLTBmZjEtY2UwMC0wMDAwMDAwMDAwMDAvc2hhcmVwb2ludEAyMDBhOGU3OS1hOThlLTRiNzktYTZlMy1jNjM3YzY0ODI0NzEiLCJpc3MiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0xMTExMTExMTExMTFAMjAwYThlNzktYTk4ZS00Yjc5LWE2ZTMtYzYzN2M2NDgyNDcxIiwibmJmIjoiMTQ0NDc4MDgwMCIsImV4cCI6IjE0NDQ4MjQwMDAiLCJuYW1laWQiOiI0ZWJiOGY4Ni1iNDBjLTRjYzUtODI1NS00ZWJlZWEwMThkYzVAMjAwYThlNzktYTk4ZS00Yjc5LWE2ZTMtYzYzN2M2NDgyNDcxIiwidHJ1c3RlZGZvcmRlbGVnYXRpb24iOiJ0cnVlIn0.ug03mm3q6yinrqwT4MrwK-xRYTlND17NpzrNo4fjJNEVcsflcjmFMjFXAeaORCR-FNJrNnt5BMMRlTilwmOa9FnYqviA4GK-hKIkDFAs_GmmzidIBe72pX88dX375HO3bccLpVu_Q_9IcYD6j247PdRN0MgX2SJmrZ5BMoCEAcbwYqbGTyBgomSPs6rqgE5sTI5Pklk9p_gLKc-14PhkR9i-SAc9NwFSkBuun3GUxkMXOLkLN_pcN5wXlBvk6wumCC2VrAKXTevuSVp_qqGdSEWPKVxhbZtUYwNhq3WOCtZjroBsuUs4at4LpOTBjyH766ANg_DJWO2LGIXldpAGHA"}}
         * */
        Map<String, Object> headers = new HashMap<>();
        headers.put(ReservedClaims.TypeExpression, ReservedClaims.JsonWebToken);

        claims.put(ReservedClaims.Audience, audience);
        claims.put(ReservedClaims.Issuer, issuer);
        claims.put(ReservedClaims.NotBefore, String.valueOf(strvalidFrom/1000));
        claims.put(ReservedClaims.ExpiresOn, String.valueOf(strvalidTo/1000));

        JwtBuilder builder = Jwts.builder().setId(id)
                .setHeader(headers)
                .setClaims(claims);

        return builder.compact();
    }


    private void getX509PrivateKey() {

        char[] kpass;
        int i;

        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            kpass = new char[KeyPassword.length()];
            for (i = 0; i < KeyPassword.length(); i++)
                kpass[i] = KeyPassword.charAt(i);
            InputStream ksfis = new ClassPathResource("HightTrust.keystore").getInputStream();
            BufferedInputStream ksbufin = new BufferedInputStream(ksfis);

            ks.load(ksbufin, kpass);
            x5tPrivateKey = (PrivateKey) ks.getKey(keyAliasName, kpass);

            /* jwt header include  xt5 ，X509Certificate Thumbprint */
            byte[] der = ks.getCertificate(keyAliasName).getEncoded();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(der);
            byte[] digest = md.digest();
            x5tThumbPrint = TextCodec.BASE64URL.encode(digest);
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
