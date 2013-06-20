package org.weasis.isowriter;

import java.util.Hashtable;

import org.weasis.dicom.explorer.DicomExportFactory;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExportDicom;
import org.weasis.dicom.explorer.ExportTree;

public class ExportPageFactory implements DicomExportFactory {

    @Override
    public ExportDicom createDicomExportPage(Hashtable<String, Object> properties) {
        if (properties != null) {
            DicomModel dicomModel = (DicomModel) properties.get(DicomModel.class.getName());
            ExportTree exportTree = (ExportTree) properties.get(ExportTree.class.getName());
            if (dicomModel != null && exportTree != null) {
                return new IsoImageExport(dicomModel, exportTree);
            }
        }
        return null;
    }

}
