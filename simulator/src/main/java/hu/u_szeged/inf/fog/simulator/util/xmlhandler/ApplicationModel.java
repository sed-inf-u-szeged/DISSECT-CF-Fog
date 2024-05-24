package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement(name = "application")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ApplicationModel {

    public String name;
    public long freq;
    public long tasksize;
    public String instance;
    public double countOfInstructions;
    public double activationRatio;
    public double transferDevider;
    public String strategy;
    public boolean canJoin;

    @XmlAttribute(name = "name", required = true)
    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "tasksize")
    public void setTasksize(long tasksize) {
        this.tasksize = tasksize;
    }

    @XmlElement(name = "instance")
    public void setInstance(String instance) {
        this.instance = instance;
    }

    @XmlElement(name = "freq")
    public void setFreq(long freq) {
        this.freq = freq;
    }

    @XmlElement(name = "countOfInstructions")
    public void setCountOfInstructions(double countOfInstructions) {
        this.countOfInstructions = countOfInstructions;
    }

    @XmlElement(name = "transferDevider")
    public void setTransferDevider(double transferDevider) {
        this.transferDevider = transferDevider;
    }

    @XmlElement(name = "activationRatio")
    public void setActivationRatio(double activationRatio) {
        this.activationRatio = activationRatio;
    }

    @XmlElement(name = "strategy")
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    @XmlElement(name = "canJoin")
    public void setCanJoin(boolean canJoin) {
        this.canJoin = canJoin;
    }

    @Override
    public String toString() {
        return "ApplicationModel [name=" + name + ", tasksize=" + tasksize + ", freq=" + freq + ", instance=" + instance
                + ", countOfInstructions=" + countOfInstructions + ", activationRatio=" + activationRatio
                + ", transferDevider=" + transferDevider + ", strategy=" + strategy + ", canJoin=" + canJoin + "]";
    }

}