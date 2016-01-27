package org.tmf.dsmapi.appointment;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tmf.dsmapi.commons.facade.AbstractFacade;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.tmf.dsmapi.commons.exceptions.BadUsageException;
import org.tmf.dsmapi.commons.exceptions.ExceptionType;
import org.tmf.dsmapi.commons.exceptions.UnknownResourceException;
import org.tmf.dsmapi.commons.utils.BeanUtils;
import org.tmf.dsmapi.appointment.model.Appointment;
import org.tmf.dsmapi.appointment.event.AppointmentEventPublisherLocal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tmf.dsmapi.appointment.model.AppointmentStatusEnum;
import org.tmf.dsmapi.appointment.model.RelatedObject;
import org.tmf.dsmapi.appointment.model.RelatedPartyRef;

@Stateless
public class AppointmentFacade extends AbstractFacade<Appointment> {

    @PersistenceContext(unitName = "DSAppointmentPU")
    private EntityManager em;
    @EJB
    AppointmentEventPublisherLocal publisher;
    StateModelImpl stateModel = new StateModelImpl();

    public AppointmentFacade() {
        super(Appointment.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public void checkCreation(Appointment entity) throws BadUsageException, UnknownResourceException {

        Appointment ap = null;
        //generate id or verify is not existe.
        if (entity.getId() == null
                || entity.getId().isEmpty()) {
//            throw new BadUsageException(ExceptionType.BAD_USAGE_GENERIC, "While creating Appointment, id must be not null");
            //Do nothing create ok
            Logger.getLogger(AppointmentFacade.class.getName()).log(Level.INFO, "Appointment with autogenerated id is being posted");
        } else {
            try {
                ap = this.find(entity.getId());
                if (null != ap) {
                    throw new BadUsageException(ExceptionType.BAD_USAGE_GENERIC,
                            "Duplicate Exception, Appointment with same id :" + entity.getId() + " alreay exists");
                }
            } catch (UnknownResourceException ex) {
                //Do nothing create ok
                Logger.getLogger(AppointmentFacade.class.getName()).log(Level.INFO, "Appointment with id = " + entity.getId() + " is being posted", ex);
            }
        }

        //verify first status
        if (null == entity.getStatus()) {
            entity.setStatus(AppointmentStatusEnum.INITIALISED);
//            throw new BadUsageException(ExceptionType.BAD_USAGE_MANDATORY_FIELDS, "LifecycleState is mandatory");
        } else {
            if (!entity.getStatus().name().equalsIgnoreCase(AppointmentStatusEnum.INITIALISED.name())) {
                throw new BadUsageException(ExceptionType.BAD_USAGE_FLOW_TRANSITION, "lifecycleState " + entity.getStatus().value() + " is not the first state, attempt : " + AppointmentStatusEnum.INITIALISED.value());
            }
        }
        //category is mandatory
        if (null == entity.getCategory()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_MANDATORY_FIELDS, "Category is mandatory");
        }

        if (null == entity.getStartDate()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_MANDATORY_FIELDS, "StartDate is mandatory");
        }

        if (null == entity.getEndDate()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_MANDATORY_FIELDS, "EndDate is mandatory");
        }

        if (entity.isAlarm()) {
            if (null == entity.getAlarmAction() || entity.getAlarmAction().isEmpty()) {
                throw new BadUsageException(ExceptionType.BAD_USAGE_OPERATOR, "If Alarm is true, alarmAction must be filled");
            }
        }

        if (null == entity.getAddress()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_MANDATORY_FIELDS, "Address id or href must be field is mandatory");
        }

        if (null == entity.getRelatedParty() || entity.getRelatedParty().isEmpty()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_MANDATORY_FIELDS, "At least one party  must be linked to the appointment");
        } else {
            for (RelatedPartyRef rpr : entity.getRelatedParty()) {
                if ((null == rpr.getId() || rpr.getId().isEmpty())
                        && (null == rpr.getHref() || rpr.getHref().isEmpty())) {
                    throw new BadUsageException(ExceptionType.BAD_USAGE_MANDATORY_FIELDS, "relatedParty id or href must be filled");
                }
            }
        }
        if (null != entity.getRelatedObject()) {
            for (RelatedObject ro : entity.getRelatedObject()) {
                if ((null == ro.getInvolvement() || ro.getInvolvement().isEmpty())
                        && (null == ro.getReference() || ro.getReference().isEmpty())) {
                    throw new BadUsageException(ExceptionType.BAD_USAGE_MANDATORY_FIELDS,
                            "If relatedObject is selected Involvement and reference must be filled");
                }
            }
        }

    }

    public Appointment patchAttributs(String id, Appointment partialEntity) throws UnknownResourceException, BadUsageException {
        Appointment currentEntity = this.find(id);

        if (currentEntity == null) {
            throw new UnknownResourceException(ExceptionType.UNKNOWN_RESOURCE);
        }
        verifyStatus(currentEntity, partialEntity);
        checkPatchAttributs(partialEntity);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.convertValue(partialEntity, JsonNode.class);
        partialEntity.setId(id);
        if (BeanUtils.patch(currentEntity, partialEntity, node)) {
            publisher.valueChangedNotification(currentEntity, new Date());
        }

        return currentEntity;
    }

    public void checkPatchAttributs(Appointment patchEntity) throws UnknownResourceException, BadUsageException {
        if (null != patchEntity.getId()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_OPERATOR, "id is not patchable");
        }
        if (null != patchEntity.getHref()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_OPERATOR, "href is not patchable");
        }
        if (null != patchEntity.getExternalId()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_OPERATOR, "externalID is not patchable");
        }
        if (null != patchEntity.getCreationDate()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_OPERATOR, "creationDate is not patchable");
        }
        if (null != patchEntity.getLastUpdate()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_OPERATOR, "lastUpdate is not patchable");
        }
        if (null != patchEntity.getAddress()) {
            throw new BadUsageException(ExceptionType.BAD_USAGE_OPERATOR, "address is not patchable");
        }

    }

    public void verifyStatus(Appointment currentEntity, Appointment partialEntity) throws BadUsageException {
        if (null != partialEntity.getStatus() && !partialEntity.getStatus().name().equals(currentEntity.getStatus().name())) {
            stateModel.checkTransition(currentEntity.getStatus(), partialEntity.getStatus());
            publisher.statusChangedNotification(currentEntity, new Date());
        }
    }

}