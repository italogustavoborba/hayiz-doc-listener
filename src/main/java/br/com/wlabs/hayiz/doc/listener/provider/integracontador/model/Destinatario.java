package br.com.wlabs.hayiz.doc.listener.provider.integracontador.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class Destinatario
{
    @XmlAttribute(name = "tipo", required = true)
    private String tipo;

    @XmlAttribute(name = "numero", required = true)
    private String numero;

    @XmlAttribute(name = "nome", required = true)
    private String nome;

    @XmlAttribute(name = "papel", required = true)
    private String papel;

    public String getTipo ()
    {
        return tipo;
    }

    public void setTipo (String tipo)
    {
        this.tipo = tipo;
    }

    public String getNumero ()
    {
        return numero;
    }

    public void setNumero (String numero)
    {
        this.numero = numero;
    }

    public String getNome ()
    {
        return nome;
    }

    public void setNome (String nome)
    {
        this.nome = nome;
    }

    public String getPapel ()
    {
        return papel;
    }

    public void setPapel (String papel)
    {
        this.papel = papel;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [tipo = "+tipo+", numero = "+numero+", nome = "+nome+", papel = "+papel+"]";
    }
}
