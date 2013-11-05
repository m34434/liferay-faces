/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.faces.demos.bean;

import java.io.Serializable;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.el.ELResolver;
import javax.enterprise.context.RequestScoped;
import javax.faces.component.UICommand;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.event.ValueChangeEvent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.portlet.ActionResponse;
import javax.xml.namespace.QName;

import com.liferay.faces.bridge.event.FileUploadEvent;
import com.liferay.faces.bridge.model.UploadedFile;
import com.liferay.faces.demos.dto.City;
import com.liferay.faces.demos.util.FacesMessageUtil;
import com.liferay.faces.util.logging.Logger;
import com.liferay.faces.util.logging.LoggerFactory;


/**
 * This is a JSF backing managed-bean for the applicant.xhtml composition.
 *
 * @author  "Neil Griffin"
 */

@Named
@RequestScoped
public class ApplicantBackingBean implements Serializable {

	// serialVersionUID
	private static final long serialVersionUID = 2947548873495692163L;

	// Logger
	private static final Logger logger = LoggerFactory.getLogger(ApplicantBackingBean.class);

	// Injections
	@Inject
	private transient ListModelBean listModelBean;

	// Self-Injections (see postConstruct method for more details)
	private transient ApplicantModelBean applicantModelBean;

	public void deleteUploadedFile(ActionEvent actionEvent) {

		UICommand uiCommand = (UICommand) actionEvent.getComponent();
		String fileId = (String) uiCommand.getValue();

		try {
			List<UploadedFile> uploadedFiles = applicantModelBean.getUploadedFiles();

			UploadedFile uploadedFileToDelete = null;

			for (UploadedFile uploadedFile : uploadedFiles) {

				if (uploadedFile.getId().equals(fileId)) {
					uploadedFileToDelete = uploadedFile;

					break;
				}
			}

			if (uploadedFileToDelete != null) {
				uploadedFileToDelete.delete();
				uploadedFiles.remove(uploadedFileToDelete);
				logger.debug("Deleted file=[{0}]", uploadedFileToDelete.getName());
			}
		}
		catch (Exception e) {
			logger.error(e);
		}
	}

	public void handleFileUpload(FileUploadEvent fileUploadEvent) throws Exception {
		List<UploadedFile> uploadedFiles = applicantModelBean.getUploadedFiles();
		UploadedFile uploadedFile = fileUploadEvent.getUploadedFile();

		if (uploadedFile != null) {

			if (uploadedFile.getStatus() == UploadedFile.Status.FILE_SAVED) {
				uploadedFiles.add(uploadedFile);
				logger.debug("Received fileName=[{0}] absolutePath=[{1}]", uploadedFile.getName(),
					uploadedFile.getAbsolutePath());
			}
			else {
				logger.error("Uploaded file status=[" + uploadedFile.getStatus().toString() + "] " +
					uploadedFile.getMessage());
				FacesMessageUtil.addGlobalUnexpectedErrorMessage(FacesContext.getCurrentInstance());
			}
		}
	}

	public void postalCodeListener(ValueChangeEvent valueChangeEvent) {

		try {
			String newPostalCode = (String) valueChangeEvent.getNewValue();
			City city = listModelBean.getCityByPostalCode(newPostalCode);

			if (city != null) {
				applicantModelBean.setAutoFillCity(city.getCityName());
				applicantModelBean.setAutoFillProvinceId(city.getProvinceId());
			}
		}
		catch (Exception e) {
			logger.error(e.getMessage(), e);
			FacesMessageUtil.addGlobalUnexpectedErrorMessage(FacesContext.getCurrentInstance());
		}
	}

	/**
	 * In JSF 2.0/2.1, beans annotated with {@link javax.faces.bean.ViewScoped} (which are managed by the JSF Managed
	 * Bean Facility) cannot be injected via the {@link javax.inject.Inject} annotation into beans that are managed by
	 * CDI. Since this class is annotated with {@link javax.enterprise.context.RequestScoped} and is managed by CDI, it
	 * is necessary to self-inject the {@link ApplicantModelBean} into this bean instance. JSF 2.2 introduced the new
	 * javax.faces.view.ViewScoped annotation which is fully compatible with CDI.
	 */
	@PostConstruct
	public void postConstruct() {

		FacesContext facesContext = FacesContext.getCurrentInstance();
		String elExpression = "applicantModelBean";
		ELResolver elResolver = facesContext.getApplication().getELResolver();
		this.applicantModelBean = (ApplicantModelBean) elResolver.getValue(facesContext.getELContext(), null,
				elExpression);
	}

	public String submit() {

		if (logger.isDebugEnabled()) {
			logger.debug("firstName=" + applicantModelBean.getFirstName());
			logger.debug("lastName=" + applicantModelBean.getLastName());
			logger.debug("emailAddress=" + applicantModelBean.getEmailAddress());
			logger.debug("phoneNumber=" + applicantModelBean.getPhoneNumber());
			logger.debug("dateOfBirth=" + applicantModelBean.getDateOfBirth());
			logger.debug("city=" + applicantModelBean.getCity());
			logger.debug("provinceId=" + applicantModelBean.getProvinceId());
			logger.debug("postalCode=" + applicantModelBean.getPostalCode());
			logger.debug("comments=" + applicantModelBean.getComments());

			List<UploadedFile> uploadedFiles = applicantModelBean.getUploadedFiles();

			for (UploadedFile uploadedFile : uploadedFiles) {
				logger.debug("uploadedFile=[{0}]", uploadedFile.getName());
			}
		}

		sendApplicationSubmissionEvent(applicantModelBean.getFirstName(), applicantModelBean.getLastName());
		
		// Delete the uploaded files.
		try {
			List<UploadedFile> uploadedFiles = applicantModelBean.getUploadedFiles();

			for (UploadedFile uploadedFile : uploadedFiles) {
				uploadedFile.delete();
				logger.debug("Deleted file=[{0}]", uploadedFile.getName());
			}

			// Store the applicant's first name in JSF 2 Flash Scope so that it can be picked up
			// for use inside of confirmation.xhtml
			FacesContext facesContext = FacesContext.getCurrentInstance();
			facesContext.getExternalContext().getFlash().put("firstName", applicantModelBean.getFirstName());

			applicantModelBean.clearProperties();

			return "success";

		}
		catch (Exception e) {
			logger.error(e.getMessage(), e);
			FacesMessageUtil.addGlobalUnexpectedErrorMessage(FacesContext.getCurrentInstance());

			return "failure";
		}
	}

	private static void sendApplicationSubmissionEvent(String firstName, String lastName)
	{
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        ActionResponse actionResponse = (ActionResponse) externalContext.getResponse();
        actionResponse.setEvent(QNAME_EVENT, firstName + " " + lastName);
	}
	
	public static final QName QNAME_EVENT = new QName("http://draexlmaier.de/events", "applicationSubmission");
}
