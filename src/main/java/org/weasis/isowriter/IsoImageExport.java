/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.isowriter;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomDirLoader;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.ExportDicom;
import org.weasis.dicom.explorer.ExportTree;
import org.weasis.dicom.explorer.LocalExport;

import com.github.stephenc.javaisotools.iso9660.ConfigException;
import com.github.stephenc.javaisotools.iso9660.ISO9660RootDirectory;
import com.github.stephenc.javaisotools.iso9660.impl.CreateISO;
import com.github.stephenc.javaisotools.iso9660.impl.ISO9660Config;
import com.github.stephenc.javaisotools.iso9660.impl.ISOImageFileHandler;
import com.github.stephenc.javaisotools.joliet.impl.JolietConfig;
import com.github.stephenc.javaisotools.rockridge.impl.RockRidgeConfig;
import com.github.stephenc.javaisotools.sabre.HandlerException;
import com.github.stephenc.javaisotools.sabre.StreamHandler;

public class IsoImageExport extends AbstractItemDialogPage implements ExportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(IsoImageExport.class);

    private static final String LAST_FOLDER = "last_folder";
    private static final String ADD_JPEG = "add_jpeg";
    private static final String ADD_VIEWER = "add_viewer";

    private final JCheckBox checkBoxAddWeasisViewer = new JCheckBox("Add Weasis viewer");
    private final JCheckBox checkBoxAddJpeg = new JCheckBox("Add JPEG images");
    private final JCheckBox checkBoxCompression = new JCheckBox("Uncompressed DICOMs");
    private final DicomModel dicomModel;
    private final ExportTree exportTree;
    private File outputFile;

    private JPanel panel;
    private final Component horizontalStrut = Box.createHorizontalStrut(20);

    public IsoImageExport(DicomModel dicomModel, CheckTreeModel treeModel) {
        super("Burn CD/DVD");
        this.dicomModel = dicomModel;
        this.exportTree = new ExportTree(treeModel);
        initGUI();
        initialize(true);
    }

    public void initGUI() {
        setLayout(new BorderLayout());
        panel = new JPanel();

        add(panel, BorderLayout.NORTH);
        GridBagLayout gbl_panel = new GridBagLayout();

        panel.setLayout(gbl_panel);

        GridBagConstraints gbc_checkBoxAddWeasisViewer = new GridBagConstraints();
        gbc_checkBoxAddWeasisViewer.anchor = GridBagConstraints.NORTHWEST;
        gbc_checkBoxAddWeasisViewer.insets = new Insets(0, 0, 0, 5);
        gbc_checkBoxAddWeasisViewer.gridx = 0;
        gbc_checkBoxAddWeasisViewer.gridy = 0;
        panel.add(checkBoxAddWeasisViewer, gbc_checkBoxAddWeasisViewer);

        GridBagConstraints gbc_checkBoxAddJpeg = new GridBagConstraints();
        gbc_checkBoxAddJpeg.insets = new Insets(0, 0, 0, 5);
        gbc_checkBoxAddJpeg.anchor = GridBagConstraints.NORTHWEST;
        gbc_checkBoxAddJpeg.gridx = 1;
        gbc_checkBoxAddJpeg.gridy = 0;
        panel.add(checkBoxAddJpeg, gbc_checkBoxAddJpeg);

        GridBagConstraints gbc_horizontalStrut = new GridBagConstraints();
        gbc_horizontalStrut.weightx = 1.0;
        gbc_horizontalStrut.gridx = 2;
        gbc_horizontalStrut.gridy = 0;
        panel.add(horizontalStrut, gbc_horizontalStrut);

        GridBagConstraints gbc_checkBoxCompression = new GridBagConstraints();
        gbc_checkBoxCompression.anchor = GridBagConstraints.NORTHWEST;
        gbc_checkBoxCompression.insets = new Insets(0, 0, 5, 5);
        gbc_checkBoxCompression.gridx = 0;
        gbc_checkBoxCompression.gridy = 1;
        // TODO Add it in Weasis 2.0 plugin
        // panel.add(checkBoxCompression, gbc_checkBoxCompression);

        add(exportTree, BorderLayout.CENTER);
    }

    protected void initialize(boolean afirst) {
        if (afirst) {
            Properties pref = ExportIsoFactory.EXPORT_PERSISTENCE;
            checkBoxAddJpeg.setSelected(Boolean.valueOf(pref.getProperty(ADD_JPEG, "true")));
            checkBoxAddWeasisViewer.setSelected(Boolean.valueOf(pref.getProperty(ADD_VIEWER, "true")));
        }
    }

    public void resetSettingsToDefault() {
        initialize(false);
    }

    public void applyChange() {

    }

    protected void updateChanges() {
    }

    @Override
    public void closeAdditionalWindow() {
        applyChange();
    }

    @Override
    public void resetoDefaultValues() {
    }

    @Override
    public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {
        browseImgFile();
        if (outputFile != null) {
            final File exportFile = outputFile.getCanonicalFile();
            ExplorerTask task = new ExplorerTask("Exporting...", false) {

                @Override
                protected Boolean doInBackground() throws Exception {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart,
                        dicomModel, null, this));
                    File exportDir = FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "burn"));
                    writeDicom(this, exportDir, model);
                    if (checkBoxAddJpeg.isSelected()) {
                        writeJpeg(this, new File(exportDir, "JPEG"), model, true, 90);
                    }
                    if (checkBoxAddWeasisViewer.isSelected()) {
                        URL url = ResourceUtil.getResourceURL("lib/weasis-distributions.zip", this.getClass());
                        if (url == null) {
                            LOGGER.error("Cannot find the embedded portable distribution");
                        } else {
                            FileUtil.unzip(url.openStream(), exportDir);
                        }
                    }
                    if (this.isCancelled()) {
                        return false;
                    }
                    makeISO(exportDir, exportFile);

                    return true;
                }

                @Override
                protected void done() {
                    Properties pref = ExportIsoFactory.EXPORT_PERSISTENCE;
                    pref.setProperty(ADD_JPEG, String.valueOf(checkBoxAddJpeg.isSelected()));
                    pref.setProperty(ADD_VIEWER, String.valueOf(checkBoxAddWeasisViewer.isSelected()));

                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop,
                        dicomModel, null, this));
                }

            };
            task.execute();
        }
    }

    public void browseImgFile() {
        String lastFolder = ExportIsoFactory.EXPORT_PERSISTENCE.getProperty(LAST_FOLDER, null);
        if (lastFolder == null) {
            lastFolder = System.getProperty("user.home", "");
        }
        outputFile = new File(lastFolder, "cdrom-DICOM.iso");

        JFileChooser fileChooser = new JFileChooser(outputFile);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        FileFormatFilter filter = new FileFormatFilter("iso", "ISO");
        fileChooser.addChoosableFileFilter(filter);
        fileChooser.setFileFilter(filter);

        fileChooser.setSelectedFile(outputFile);
        File file = null;
        if (fileChooser.showSaveDialog(this) != 0 || (file = fileChooser.getSelectedFile()) == null) {
            outputFile = null;
            return;
        } else {
            outputFile = file;
            ExportIsoFactory.EXPORT_PERSISTENCE.setProperty(LAST_FOLDER, file.getParent());
        }
    }

    private String getinstanceFileName(DicomImageElement img) {
        Integer instance = (Integer) img.getTagValue(TagW.InstanceNumber);
        if (instance != null) {
            String val = instance.toString();
            if (val.length() < 5) {
                char[] chars = new char[5 - val.length()];
                for (int i = 0; i < chars.length; i++) {
                    chars[i] = '0';
                }
                return new String(chars) + val;

            } else {
                return val;
            }
        }
        return (String) img.getTagValue(TagW.SOPInstanceUID);
    }

    private void writeJpeg(ExplorerTask task, File exportDir, CheckTreeModel model, boolean keepNames, int jpegQuality) {
        synchronized (model) {
            TreePath[] paths = model.getCheckingPaths();
            for (TreePath treePath : paths) {
                if (task.isCancelled()) {
                    return;
                }
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                if (node.getUserObject() instanceof DicomImageElement) {
                    DicomImageElement img = (DicomImageElement) node.getUserObject();
                    // Get instance number instead SOPInstanceUID to handle multiframe
                    String instance = getinstanceFileName(img);
                    StringBuffer buffer = new StringBuffer();
                    if (keepNames) {
                        TreeNode[] objects = node.getPath();
                        if (objects.length > 3) {
                            buffer.append(FileUtil.getValidFileNameWithoutHTML(objects[1].toString()));
                            buffer.append(File.separator);
                            buffer.append(FileUtil.getValidFileNameWithoutHTML(objects[2].toString()));
                            buffer.append(File.separator);
                            String seriesName = FileUtil.getValidFileNameWithoutHTML(objects[3].toString());
                            if (seriesName.length() > 30) {
                                buffer.append(seriesName, 0, 27);
                                buffer.append("...");
                            } else {
                                buffer.append(seriesName);
                            }
                            buffer.append('-');
                            // Hash of UID to guaranty the unique behavior of the name (can have only series number).
                            buffer.append(LocalExport.makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                        }
                    } else {
                        buffer.append(LocalExport.makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
                        buffer.append(File.separator);
                        buffer.append(LocalExport.makeFileIDs((String) img.getTagValue(TagW.StudyInstanceUID)));
                        buffer.append(File.separator);
                        buffer.append(LocalExport.makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                        instance = LocalExport.makeFileIDs(instance);
                    }

                    File destinationDir = new File(exportDir, buffer.toString());
                    destinationDir.mkdirs();

                    RenderedImage image = img.getImage(null);

                    if (image != null) {
                        image = img.getRenderedImage(image);
                    }
                    if (image != null) {
                        ImageFiler.writeJPG(new File(destinationDir, instance + ".jpg"), image, jpegQuality / 100.0f); //$NON-NLS-1$
                    } else {
                        LOGGER.error("Cannot export DICOM file to jpeg: {}", img.getFile()); //$NON-NLS-1$
                    }
                    // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
                    img.removeImageFromCache();
                }
            }
        }

    }

    private void writeDicom(ExplorerTask task, File exportDir, CheckTreeModel model) throws IOException {
        DicomDirWriter writer = null;
        try {
            File dcmdirFile = new File(exportDir, "DICOMDIR"); //$NON-NLS-1$
            writer = DicomDirLoader.open(dcmdirFile);

            synchronized (model) {
                ArrayList<String> uids = new ArrayList<String>();
                TreePath[] paths = model.getCheckingPaths();
                TreePath: for (TreePath treePath : paths) {
                    if (task.isCancelled()) {
                        return;
                    }
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                    if (node.getUserObject() instanceof DicomImageElement) {
                        DicomImageElement img = (DicomImageElement) node.getUserObject();
                        String iuid = (String) img.getTagValue(TagW.SOPInstanceUID);
                        int index = uids.indexOf(iuid);
                        if (index == -1) {
                            uids.add(iuid);
                        } else {
                            // Write only once the file for multiframe
                            continue;
                        }
                        StringBuffer buffer = new StringBuffer();

                        buffer.append("DICOM"); //$NON-NLS-1$
                        buffer.append(File.separator);

                        buffer.append(LocalExport.makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
                        buffer.append(File.separator);
                        buffer.append(LocalExport.makeFileIDs((String) img.getTagValue(TagW.StudyInstanceUID)));
                        buffer.append(File.separator);
                        buffer.append(LocalExport.makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                        iuid = LocalExport.makeFileIDs(iuid);

                        File destinationDir = new File(exportDir, buffer.toString());
                        destinationDir.mkdirs();

                        File destinationFile = new File(destinationDir, iuid);
                        if (FileUtil.nioCopyFile(img.getFile(), destinationFile)) {
                            if (writer != null) {
                                Attributes fmi = null;
                                Attributes dataset = null;
                                DicomMediaIO dicomImageLoader = (DicomMediaIO) img.getMediaReader();
                                dataset = dicomImageLoader.getDicomObject();
                                if (dataset == null) {
                                    LOGGER.error("Cannot export DICOM file: ", img.getFile()); //$NON-NLS-1$
                                    continue TreePath;
                                }
                                fmi = dataset.createFileMetaInformation(UID.ImplicitVRLittleEndian);

                                String miuid = fmi.getString(Tag.MediaStorageSOPInstanceUID, null);

                                String pid = dataset.getString(Tag.PatientID, null);
                                String styuid = dataset.getString(Tag.StudyInstanceUID, null);
                                String seruid = dataset.getString(Tag.SeriesInstanceUID, null);

                                if (styuid != null && seruid != null) {
                                    if (pid == null) {
                                        dataset.setString(Tag.PatientID, VR.LO, pid = styuid);
                                    }
                                    Attributes patRec = writer.findPatientRecord(pid);
                                    if (patRec == null) {
                                        patRec =
                                            DicomDirLoader.RecordFactory.createRecord(RecordType.PATIENT, null,
                                                dataset, null, null);
                                        writer.addRootDirectoryRecord(patRec);
                                    }
                                    Attributes studyRec = writer.findStudyRecord(patRec, styuid);
                                    if (studyRec == null) {
                                        studyRec =
                                            DicomDirLoader.RecordFactory.createRecord(RecordType.STUDY, null, dataset,
                                                null, null);
                                        writer.addLowerDirectoryRecord(patRec, studyRec);
                                    }
                                    Attributes seriesRec = writer.findSeriesRecord(studyRec, seruid);
                                    if (seriesRec == null) {
                                        seriesRec =
                                            DicomDirLoader.RecordFactory.createRecord(RecordType.SERIES, null, dataset,
                                                null, null);
                                        // Icon Image Sequence (0088,0200).This Icon Image is representative of the
                                        // Series.
                                        // It may or may not correspond to one of the images of the Series.
                                        if (seriesRec != null && node.getParent() instanceof DefaultMutableTreeNode) {
                                            DicomImageElement midImage =
                                                ((DicomSeries) ((DefaultMutableTreeNode) node.getParent())
                                                    .getUserObject()).getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null,
                                                    null);
                                            Attributes iconItem = LocalExport.mkIconItem(midImage);
                                            if (iconItem != null) {
                                                seriesRec.newSequence(Tag.IconImageSequence, 1).add(iconItem);
                                            }
                                        }
                                        writer.addLowerDirectoryRecord(studyRec, seriesRec);
                                    }
                                    Attributes instRec;
                                    if (writer.findLowerInstanceRecord(seriesRec, false, iuid) == null) {
                                        instRec =
                                            DicomDirLoader.RecordFactory.createRecord(dataset, fmi,
                                                writer.toFileIDs(destinationFile));
                                        writer.addLowerDirectoryRecord(seriesRec, instRec);
                                    }
                                } else {
                                    if (writer.findRootInstanceRecord(false, miuid) == null) {
                                        Attributes instRec =
                                            DicomDirLoader.RecordFactory.createRecord(dataset, fmi,
                                                writer.toFileIDs(destinationFile));
                                        writer.addRootDirectoryRecord(instRec);
                                    }
                                }
                            }
                        } else {
                            LOGGER.error("Cannot export DICOM file: ", img.getFile()); //$NON-NLS-1$
                        }
                    }
                }
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private File makeISO(File exportDir, File exportFile) {
        boolean enableRockRidge = true;
        boolean enableJoliet = true;

        // ISO file
        File outfile = exportFile;
        // Directory hierarchy, starting from the root
        ISO9660RootDirectory root = new ISO9660RootDirectory();

        try {
            File[] files = exportDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.exists()) {
                        if (file.isDirectory()) {
                            root.addRecursively(file);
                        } else {
                            root.addFile(file);
                        }
                    }
                }
            }
        } catch (HandlerException e) {
            e.printStackTrace();
        }

        try {
            // ISO9660 support
            ISO9660Config iso9660Config = new ISO9660Config();
            iso9660Config.allowASCII(false);
            iso9660Config.setInterchangeLevel(1);
            iso9660Config.restrictDirDepthTo8(true);
            iso9660Config.setPublisher("Weasis");
            iso9660Config.setVolumeID("DICOM");
            iso9660Config.setDataPreparer("DICOM");
            // iso9660Config.setCopyrightFile(new File("Copyright.txt"));
            iso9660Config.forceDotDelimiter(false);

            RockRidgeConfig rrConfig = null;

            if (enableRockRidge) {
                // Rock Ridge support
                rrConfig = new RockRidgeConfig();
                rrConfig.setMkisofsCompatibility(false);
                rrConfig.hideMovedDirectoriesStore(true);
                rrConfig.forcePortableFilenameCharacterSet(true);
            }

            JolietConfig jolietConfig = null;
            if (enableJoliet) {
                // Joliet support
                jolietConfig = new JolietConfig();
                jolietConfig.setPublisher("Weasis");
                jolietConfig.setVolumeID("DICOM");
                jolietConfig.setDataPreparer("DICOM");
                // jolietConfig.setCopyrightFile(new File("Copyright.txt"));
                jolietConfig.forceDotDelimiter(false);
            }

            // Create ISO
            StreamHandler streamHandler = new ISOImageFileHandler(outfile);
            CreateISO iso = new CreateISO(streamHandler, root);
            iso.process(iso9660Config, rrConfig, jolietConfig, null);
            return outfile;

        } catch (ConfigException e) {
            e.printStackTrace();
        } catch (HandlerException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            FileUtil.recursiveDelete(exportDir);
        }
        return null;
    }

}
