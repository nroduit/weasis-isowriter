package org.weasis.isowriter;

import java.io.File;
import java.util.Hashtable;
import java.util.Properties;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomExportFactory;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportDicom;


@org.osgi.service.component.annotations.Component(service = DicomExportFactory.class, immediate = false)
public class ExportIsoFactory implements DicomExportFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportIsoFactory.class);

    public static final Properties EXPORT_PERSISTENCE = new Properties();

    @Override
    public ExportDicom createDicomExportPage(Hashtable<String, Object> properties) {
        if (properties != null) {
            DicomModel dicomModel = (DicomModel) properties.get(DicomModel.class.getName());
            CheckTreeModel treeModel = (CheckTreeModel) properties.get(CheckTreeModel.class.getName());
            if (dicomModel != null && treeModel != null) {
                return new IsoImageExport(dicomModel, treeModel);
            }
        }
        return null;
    }

    @Activate
    protected void activate(ComponentContext context) throws Exception {
        LOGGER.info("Export ISO image is activated");
        FileUtil.readProperties(new File(BundlePreferences.getDataFolder(context.getBundleContext()),
            "export.properties"), EXPORT_PERSISTENCE);//$NON-NLS-1$
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        LOGGER.info("Export ISO image is deactivated");
        FileUtil.storeProperties(new File(BundlePreferences.getDataFolder(context.getBundleContext()),
            "export.properties"), EXPORT_PERSISTENCE, null);//$NON-NLS-1$

    }
}
