package org.tdar.web.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tdar.core.bean.PersonalFilestoreTicket;
import org.tdar.core.bean.billing.BillingAccount;
import org.tdar.core.bean.entity.TdarUser;
import org.tdar.core.bean.file.TdarDir;
import org.tdar.core.exception.FileUploadException;
import org.tdar.core.service.PersonalFilestoreService;

import com.opensymphony.xwork2.TextProvider;

@Service
public class WebPersonalFilestoreService {

    private final transient Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private PersonalFilestoreService filestoreService;

    @Transactional(readOnly = false)
    public PersonalFilestoreTicket grabTicket(TdarUser authenticatedUser) {
        return filestoreService.createPersonalFilestoreTicket(authenticatedUser);
    }

    @Transactional(readOnly = false)
    public List<String> store(TdarUser submitter, List<File> files, List<String> fileNames, List<String> contentTypes, PersonalFilestoreTicket ticket,
            TextProvider provider, BillingAccount account, TdarDir dir) throws FileUploadException {
        List<String> hashCodes = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            String fileName = fileNames.get(i);
            // put upload in holding area to be retrieved later (maybe) by the informationResourceController
            if ((file != null) && file.exists()) {
                String contentType = "";
                if (contentTypes.size() > i) {
                    contentType = contentTypes.get(i);
                }
                logger.debug("UPLOAD CONTROLLER: processing file: {} ({}) , contentType: {} , tkt: {}", fileName, file, contentType, ticket.getId());
                filestoreService.store(ticket, file, fileName, account, submitter, dir);

            }
        }
        return hashCodes;
    }
}
