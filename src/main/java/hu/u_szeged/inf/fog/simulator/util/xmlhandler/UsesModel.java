package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.PROPERTY)
public class UsesModel {

    public String link;
    public String id;
    public String type;
    public int amount;
    public long activate;
    public double runtime;
    public long size;

    @XmlAttribute(name = "link")
    public void setLink(String link) {
        this.link = link;
    }

    @XmlAttribute(name = "id")
    public void setId(String id) {
        this.id = id;
    }

    @XmlAttribute(name = "type")
    public void setType(String type) {
        this.type = type;
    }

    @XmlAttribute(name = "size")
    public void setSize(long size) {
        this.size = size;
    }

    @XmlAttribute(name = "amount")
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @XmlAttribute(name = "activate")
    public void setactivate(long activate) {
        this.activate = activate;
    }

    @XmlAttribute(name = "runtime")
    public void setRuntime(double runtime) {
        this.runtime = runtime;
    }

    @Override
    public String toString() {
        return "UsesModel [link=" + link + ", id=" + id + ", type=" + type + ", amount=" + amount + ", activate="
                + activate + ", runtime=" + runtime + ", size=" + size + "]";
    }

}
