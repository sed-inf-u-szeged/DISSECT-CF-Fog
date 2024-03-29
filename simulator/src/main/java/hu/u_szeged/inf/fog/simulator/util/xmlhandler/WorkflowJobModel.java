package hu.u_szeged.inf.fog.simulator.util.xmlhandler;

import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob.Uses;
import java.io.File;
import java.util.ArrayList;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;



@XmlRootElement(name = "job")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class WorkflowJobModel {

    public String id;
    public ArrayList<UsesModel> uses;
    public double runtime;
    public double longitude;
    public double latitude;

    @XmlAttribute(name = "id", required = true)
    public void setId(String id) {
        this.id = id;
    }

    @XmlAttribute(name = "runtime")
    public void setRuntime(double runtime) {
        this.runtime = runtime;
    }

    @XmlAttribute(name = "long")
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @XmlAttribute(name = "lat")
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    @XmlElement(name = "uses")
    public void setUses(ArrayList<UsesModel> uses) {
        this.uses = uses;
    }

    public void add(UsesModel device) {
        if (this.uses == null) {
            this.uses = new ArrayList<UsesModel>();
        }
        this.uses.add(device);
    }

    public ArrayList<UsesModel> getUses() {
        return uses;
    }

    @Override
    public String toString() {
        return "WorkflowJobModel [id=" + id + ", uses=" + uses + ", runtime=" + runtime + ", longitude=" + longitude
                + ", latitude=" + latitude + "]";
    }

    public static void loadWorkflowXML(String workflowfile) throws JAXBException {

        File file = new File(workflowfile);
        JAXBContext jaxbContext = JAXBContext.newInstance(WorkflowJobsModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        WorkflowJobsModel job = (WorkflowJobsModel) jaxbUnmarshaller.unmarshal(file);

        System.out.print("Loading " + job.name + " workflow..");
        for (int repeatIndex = 0; repeatIndex < job.repeat; repeatIndex++) {

            for (int jobIndex = 0; jobIndex < job.jobList.size(); jobIndex++) {
                WorkflowJobModel wjModel = job.jobList.get(jobIndex);

                ArrayList<Uses> inputs = new ArrayList<>();
                ArrayList<Uses> outputs = new ArrayList<>();

                for (int usesIndex = 0; usesIndex < wjModel.uses.size(); usesIndex++) {

                    UsesModel usesModel = wjModel.uses.get(usesIndex);
                    if (usesModel.link.equals("input")) {
                        inputs.add(new Uses(Uses.Type.valueOf(usesModel.type.toUpperCase()), usesModel.size,
                                usesModel.runtime, usesModel.activate, usesModel.amount, usesModel.id));
                    } else {
                        outputs.add(new Uses(Uses.Type.valueOf(usesModel.type.toUpperCase()), usesModel.size,
                                usesModel.runtime, usesModel.activate, usesModel.amount,
                                repeatIndex + "_" + usesModel.id));
                    }
                }
                new WorkflowJob(repeatIndex + "_" + wjModel.id, wjModel.runtime, wjModel.longitude, wjModel.latitude,
                        WorkflowJob.State.SUBMITTED, inputs, outputs);
            }
        }
        System.out.println(WorkflowJob.workflowJobs.size() + " jobs loaded.");
    }

}