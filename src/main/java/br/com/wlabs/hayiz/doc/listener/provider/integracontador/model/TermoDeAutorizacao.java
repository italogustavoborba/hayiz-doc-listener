package br.com.wlabs.hayiz.doc.listener.provider.integracontador.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "dados"
})
@XmlRootElement(name = "termoDeAutorizacao")
public class TermoDeAutorizacao
{
    private Dados dados;

    public Dados getDados ()
    {
        return dados;
    }

    public void setDados (Dados dados)
    {
        this.dados = dados;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [dados = "+dados+"]";
    }
}