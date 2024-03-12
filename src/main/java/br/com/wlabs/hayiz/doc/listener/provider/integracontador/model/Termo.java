package br.com.wlabs.hayiz.doc.listener.provider.integracontador.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class Termo
{
    @XmlAttribute(name = "texto", required = true)
    private String texto;

    public String getTexto ()
    {
        return texto;
    }

    public void setTexto (String texto)
    {
        this.texto = texto;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [texto = "+texto+"]";
    }
}
