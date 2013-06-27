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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.StopTagInputHandler;
import org.dcm4che2.media.ApplicationProfile;
import org.dcm4che2.media.DicomDirWriter;
import org.dcm4che2.media.FileSetInformation;
import org.dcm4che2.media.StdGenJPEGApplicationProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
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

    private static final File BURN_DIR = AbstractProperties.buildAccessibleTempDirecotry("burn");

    private final JCheckBox checkBoxAddWeasisViewer = new JCheckBox("Add Weasis viewer");
    private final JCheckBox checkBoxAddJpeg = new JCheckBox("Add JPEG images");
    private final JCheckBox checkBoxCompression = new JCheckBox("Uncompressed DICOMs");
    private final DicomModel dicomModel;
    private final ExportTree exportTree;

    private JPanel panel;
    private final Component horizontalStrut = Box.createHorizontalStrut(20);

    public IsoImageExport(DicomModel dicomModel, ExportTree exportTree) {
        super("Burn CD/DVD");
        this.dicomModel = dicomModel;
        this.exportTree = exportTree;
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
            // TODO make local prefs? Add BuldlePreference
            checkBoxAddJpeg.setSelected(true);
            checkBoxAddWeasisViewer.setSelected(true);
            TreePath rootPath = new TreePath(exportTree.getRootNode());
            exportTree.getTree().addCheckingPath(rootPath);
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
    public void exportDICOM(final ExportTree tree, JProgressBar info) throws IOException {

        ExplorerTask task = new ExplorerTask("Burning...") { //$NON-NLS-1$

                @Override
                protected Boolean doInBackground() throws Exception {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart,
                        dicomModel, null, this));
                    File exportDir = createTempDir();
                    writeDicom(exportDir, tree);
                    if (checkBoxAddJpeg.isSelected()) {
                        // TODO issue do not close stream!
                        writeJpeg(new File(exportDir, "JPEG"), tree, true, 90);
                    }
                    if (checkBoxAddWeasisViewer.isSelected()) {
                        // TODO set dependency in Maven
                        String path = "/home/nicolas/Share Apps/DICOM/Weasis/1.2.5/weasis-portable.zip";
                        File file = new File(path);
                        if (file.canRead()) {
                            unzip(file, exportDir);
                        }
                    }
                    makeISO(exportDir);
                    return true;
                }

                @Override
                protected void done() {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop,
                        dicomModel, null, this));
                }

            };
        task.execute();

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

    private void writeJpeg(File exportDir, ExportTree tree, boolean keepNames, int jpegQuality) {
        synchronized (tree) {
            TreePath[] paths = tree.getTree().getCheckingPaths();
            for (TreePath treePath : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

                if (node.getUserObject() instanceof DicomImageElement) {
                    DicomImageElement img = (DicomImageElement) node.getUserObject();
                    // Get instance number instead SOPInstanceUID to handle multiframe
                    String instance = getinstanceFileName(img);
                    StringBuffer buffer = new StringBuffer();
                    if (keepNames) {
                        TreeNode[] objects = node.getPath();
                        if (objects.length > 3) {
                            buffer.append(FileUtil.getValidFileName(objects[1].toString()));
                            buffer.append(File.separator);
                            buffer.append(FileUtil.getValidFileName(objects[2].toString()));
                            buffer.append(File.separator);
                            String seriesName = FileUtil.getValidFileName(objects[3].toString());
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
                }
            }
        }

    }

    private void writeDicom(File exportDir, ExportTree tree) throws IOException {
        ApplicationProfile dicomStruct = new StdGenJPEGApplicationProfile();
        DicomDirWriter writer = null;
        try {

            File dcmdirFile = new File(exportDir, "DICOMDIR"); //$NON-NLS-1$
            if (dcmdirFile.createNewFile()) {
                FileSetInformation fsinfo = new FileSetInformation();
                fsinfo.init();
                writer = new DicomDirWriter(dcmdirFile, fsinfo);
            } else {
                writer = new DicomDirWriter(dcmdirFile);
            }

            synchronized (tree) {
                ArrayList<String> uids = new ArrayList<String>();
                TreePath[] paths = tree.getTree().getCheckingPaths();
                for (TreePath treePath : paths) {
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
                        boolean newSeries = destinationDir.mkdirs();

                        File destinationFile = new File(destinationDir, iuid);
                        if (FileUtil.nioCopyFile(img.getFile(), destinationFile)) {
                            if (writer != null) {
                                DicomInputStream in = null;
                                DicomObject dcmobj;
                                try {
                                    in = new DicomInputStream(destinationFile);
                                    in.setHandler(new StopTagInputHandler(Tag.PixelData));
                                    dcmobj = in.readDicomObject();
                                } finally {
                                    FileUtil.safeClose(in);
                                }
                                DicomObject patrec = dicomStruct.makePatientDirectoryRecord(dcmobj);
                                DicomObject styrec = dicomStruct.makeStudyDirectoryRecord(dcmobj);
                                DicomObject serrec = dicomStruct.makeSeriesDirectoryRecord(dcmobj);

                                // Icon Image Sequence (0088,0200).This Icon Image is representative of the Series. It
                                // may or may not correspond to one of the images of the Series.
                                if (newSeries && node.getParent() instanceof DefaultMutableTreeNode) {
                                    DicomImageElement midImage =
                                        ((DicomSeries) ((DefaultMutableTreeNode) node.getParent()).getUserObject())
                                            .getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
                                    DicomObject seq = LocalExport.mkIconItem(midImage);
                                    if (seq != null) {
                                        serrec.putNestedDicomObject(Tag.IconImageSequence, seq);
                                    }
                                }

                                DicomObject instrec =
                                    dicomStruct.makeInstanceDirectoryRecord(dcmobj, writer.toFileID(destinationFile));
                                DicomObject rec = writer.addPatientRecord(patrec);
                                rec = writer.addStudyRecord(rec, styrec);
                                rec = writer.addSeriesRecord(rec, serrec);
                                String miuid = dcmobj.getString(Tag.MediaStorageSOPInstanceUID);
                                if (writer.findInstanceRecord(rec, miuid) == null) {
                                    writer.addChildRecord(rec, instrec);
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

    private File makeISO(File exportDir) {
        boolean enableRockRidge = true;
        boolean enableJoliet = true;

        // ISO file
        File outfile = new File(exportDir.getParent(), exportDir.getName() + ".iso");
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
        }
        return null;
    }

    // Methods that are in Weasis 2.0
    // //////////////////////////////////////////////////////////////////////////////////////////////
    public static File createTempDir() {
        String baseName = String.valueOf(System.currentTimeMillis());

        for (int counter = 0; counter < 1000; counter++) {
            File tempDir = new File(BURN_DIR, baseName + counter);
            if (tempDir.mkdir()) {
                return tempDir;
            }
        }
        throw new IllegalStateException("Failed to create directory"); //$NON-NLS-1$
    }

    public static void zip(File directory, File zipfile) throws IOException {
        URI base = directory.toURI();
        Deque<File> queue = new LinkedList<File>();
        queue.push(directory);
        OutputStream out = null;
        ZipOutputStream zout = null;
        try {
            out = new FileOutputStream(zipfile);
            zout = new ZipOutputStream(out);
            while (!queue.isEmpty()) {
                directory = queue.pop();
                for (File entry : directory.listFiles()) {
                    String name = base.relativize(entry.toURI()).getPath();
                    if (entry.isDirectory()) {
                        queue.push(entry);
                        if (entry.list().length == 0) {
                            name = name.endsWith("/") ? name : name + "/"; //$NON-NLS-1$ //$NON-NLS-2$
                            zout.putNextEntry(new ZipEntry(name));
                        }
                    } else {
                        zout.putNextEntry(new ZipEntry(name));
                        copyZip(entry, zout);
                        zout.closeEntry();
                    }
                }
            }
        } finally {
            // Zip stream must be close before out stream.
            FileUtil.safeClose(zout);
            FileUtil.safeClose(out);
        }
    }

    public static void unzip(File zipfile, File directory) throws IOException {
        ZipFile zfile = new ZipFile(zipfile);
        Enumeration<? extends ZipEntry> entries = zfile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File file = new File(directory, entry.getName());
            if (entry.isDirectory()) {
                file.mkdirs();
            } else {
                file.getParentFile().mkdirs();
                InputStream in = zfile.getInputStream(entry);
                try {
                    copyZip(in, file);
                } finally {
                    in.close();
                }
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null) {
            return;
        }
        byte[] buf = new byte[FileUtil.FILE_BUFFER];
        int offset;
        while ((offset = in.read(buf)) > 0) {
            out.write(buf, 0, offset);
        }
        out.flush();
    }

    private static void copyZip(File file, OutputStream out) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            copy(in, out);
        } finally {
            in.close();
        }
    }

    private static void copyZip(InputStream in, File file) throws IOException {
        OutputStream out = new FileOutputStream(file);
        try {
            copy(in, out);
        } finally {
            out.close();
        }
    }
}
