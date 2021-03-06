package org.tdar.struts.action.video;

import java.util.Set;

import org.apache.struts2.convention.annotation.Namespace;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.tdar.core.bean.resource.ResourceType;
import org.tdar.core.bean.resource.Video;
import org.tdar.struts.action.resource.AbstractInformationResourceController;

/**
 * $Id$
 * 
 * <p>
 * Manages requests to create/delete/edit a Video and its associated metadata.
 * </p>
 * 
 * 
 * @author <a href='mailto:Adam.Brin@asu.edu'>Adam Brin</a>
 * @version $Revision$
 */
@ParentPackage("secured")
@Component
@Scope("prototype")
@Namespace("/video")
public class VideoController extends AbstractInformationResourceController<Video> {

    private static final long serialVersionUID = -6872812317910152508L;

    @Override
    public Set<String> getValidFileExtensions() {
        return getExtensionsForType(ResourceType.VIDEO);
    }

    @Override
    public boolean isMultipleFileUploadEnabled() {
        return true;
    }

    public void setVideo(Video video) {
        setPersistable(video);
    }

    public Video getVideo() {
        return getPersistable();
    }

    @Override
    public Class<Video> getPersistableClass() {
        return Video.class;
    }

    @Override
    public Video getResource() {
        if (getPersistable() == null) {
            setPersistable(createPersistable());
        }
        return getPersistable();
    }
}
