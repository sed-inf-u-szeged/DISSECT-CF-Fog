package hu.u_szeged.inf.fog.simulator.util.xml;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This class is annotated with JAXB annotations to map Java classes to XML representations and vice versa.
 * It is responsible for XML driven simulation, in this case loading IoT applications from a file.
 * Example files are located in: src/main/resources/demo/XML_examples
 */
@XmlRootElement(name = "applications")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ApplicationsXmlModel {
    
    ArrayList<ApplicationXmlModel> applicationList;

    public ArrayList<ApplicationXmlModel> getApplications() {
        return applicationList;
    }

    @XmlElement(name = "application")
    public void setApplications(ArrayList<ApplicationXmlModel> application) {
        this.applicationList = application;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        for (ApplicationXmlModel am : this.applicationList) {
            str.append(am.toString());
            str.append("\n");
        }
        return str.toString();
    }
}