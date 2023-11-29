package br.com.wlabs.hayiz.doc.listener.integration.capmonster.model;

public class ImageToTextTask extends Captcha {

    public ImageToTextTask() {
        super();
        params.put("type", "ImageToTextTask");
        params.put("recognizingThreshold", 95);
        //params.put("Case", true);
        //params.put("CapMonsterModule", "solvemedia");
    }

    public void setBody(String body) {
        params.put("body", body);
    }

    public void setRecognizingThreshold(int recognizingThreshold) {
        params.put("recognizingThreshold", recognizingThreshold);
    }

}
