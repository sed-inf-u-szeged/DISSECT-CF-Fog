package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;


@XmlRootElement(name = "applications")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class ApplicationsModel {
    ArrayList<ApplicationModel> applicationList;

    public ArrayList<ApplicationModel> getApplications() {
        return applicationList;
    }

    @XmlElement(name = "application")
    public void setApplications(ArrayList<ApplicationModel> application) {
        this.applicationList = application;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        for (ApplicationModel am : this.applicationList) {
            str.append(am.toString());
            str.append("\n");
        }
        return str.toString();
    }

}
