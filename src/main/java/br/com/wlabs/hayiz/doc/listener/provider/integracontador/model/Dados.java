package br.com.wlabs.hayiz.doc.listener.provider.integracontador.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "sistema", "termo", "avisoLegal", "finalidade", "dataAssinatura", "vigencia", "destinatario", "assinadoPor"
})
public class Dados
{
    private Vigencia vigencia;

    private Termo termo;

    private Finalidade finalidade;

    private Sistema sistema;

    private AvisoLegal avisoLegal;

    private AssinadoPor assinadoPor;

    private DataAssinatura dataAssinatura;

    private Destinatario destinatario;

    public Vigencia getVigencia ()
    {
        return vigencia;
    }

    public void setVigencia (Vigencia vigencia)
    {
        this.vigencia = vigencia;
    }

    public Termo getTermo ()
    {
        return termo;
    }

    public void setTermo (Termo termo)
    {
        this.termo = termo;
    }

    public Finalidade getFinalidade ()
    {
        return finalidade;
    }

    public void setFinalidade (Finalidade finalidade)
    {
        this.finalidade = finalidade;
    }

    public Sistema getSistema ()
    {
        return sistema;
    }

    public void setSistema (Sistema sistema)
    {
        this.sistema = sistema;
    }

    public AvisoLegal getAvisoLegal ()
    {
        return avisoLegal;
    }

    public void setAvisoLegal (AvisoLegal avisoLegal)
    {
        this.avisoLegal = avisoLegal;
    }

    public AssinadoPor getAssinadoPor ()
    {
        return assinadoPor;
    }

    public void setAssinadoPor (AssinadoPor assinadoPor)
    {
        this.assinadoPor = assinadoPor;
    }

    public DataAssinatura getDataAssinatura ()
    {
        return dataAssinatura;
    }

    public void setDataAssinatura (DataAssinatura dataAssinatura)
    {
        this.dataAssinatura = dataAssinatura;
    }

    public Destinatario getDestinatario ()
    {
        return destinatario;
    }

    public void setDestinatario (Destinatario destinatario)
    {
        this.destinatario = destinatario;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [vigencia = "+vigencia+", termo = "+termo+", finalidade = "+finalidade+", sistema = "+sistema+", avisoLegal = "+avisoLegal+", assinadoPor = "+assinadoPor+", dataAssinatura = "+dataAssinatura+", destinatario = "+destinatario+"]";
    }
}
