package hu.u_szeged.inf.fog.simulator.util.xml;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class is annotated with JAXB annotations to map Java classes to XML representations and vice versa.
 * It is responsible for XML driven simulation, in this case loading computing appliances from a file.
 * Example files are located in: src/main/resources/demo/XML_examples
 */
@XmlRootElement(name = "appliances")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class AppliancesXmlModel {

    ArrayList<ApplianceXmlModel> applianceList;

    public ArrayList<ApplianceXmlModel> getAppliances() {
        return applianceList;
    }

    @XmlElement(name = "appliance")
    public void setAppliances(ArrayList<ApplianceXmlModel> appliances) {
        this.applianceList = appliances;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        for (ApplianceXmlModel appliance : this.applianceList) {
            str.append(appliance.toString());
            str.append("\n");
        }
        return str.toString();
    }
}