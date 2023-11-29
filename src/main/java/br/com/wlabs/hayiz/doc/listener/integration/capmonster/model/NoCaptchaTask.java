package br.com.wlabs.hayiz.doc.listener.integration.capmonster.model;

public class NoCaptchaTask extends Captcha {

    public NoCaptchaTask() {
        super();
        params.put("type", "NoCaptchaTask");
    }

    public void setWebsiteKey(String websiteKey) {
        params.put("websiteKey", websiteKey);
    }

    public void setWebsiteURL(String websiteURL) {
        params.put("websiteURL", websiteURL);
    }

}
