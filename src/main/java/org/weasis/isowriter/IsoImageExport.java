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
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.media.jai.PlanarImage;
import javax.media.jai.operator.SubsampleAverageDescriptor;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
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
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.ExportDicom;
import org.weasis.dicom.explorer.ExportTree;

import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.ConfigException;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.ISO9660RootDirectory;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.CreateISO;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.ISO9660Config;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.ISOImageFileHandler;
import de.tu_darmstadt.informatik.rbg.hatlak.joliet.impl.JolietConfig;
import de.tu_darmstadt.informatik.rbg.hatlak.rockridge.impl.RockRidgeConfig;
import de.tu_darmstadt.informatik.rbg.mhartle.sabre.HandlerException;
import de.tu_darmstadt.informatik.rbg.mhartle.sabre.StreamHandler;

public class IsoImageExport extends AbstractItemDialogPage implements ExportDicom {
    private static final Logger LOGGER = LoggerFactory.getLogger(IsoImageExport.class);

    private static final char[] HEX_DIGIT = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
        'E', 'F' };

    private final DicomModel dicomModel;
    private final ExportTree exportTree;

    private JPanel panel;

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
        FlowLayout flowLayout = (FlowLayout) panel.getLayout();
        flowLayout.setAlignment(FlowLayout.LEFT);

        add(panel, BorderLayout.NORTH);

        JCheckBox checkBoxCompression = new JCheckBox("Uncompressed images");
        panel.add(checkBoxCompression);

        JCheckBox checkBoxAddJpeg = new JCheckBox("Add JPEG images");
        panel.add(checkBoxAddJpeg);

        JCheckBox checkBoxAddWeasisViewer = new JCheckBox("Add Weasis viewer");
        panel.add(checkBoxAddWeasisViewer);

        add(exportTree, BorderLayout.CENTER);
    }

    protected void initialize(boolean afirst) {
        if (afirst) {

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

        ExplorerTask task = new ExplorerTask("Exporting...") { //$NON-NLS-1$

                @Override
                protected Boolean doInBackground() throws Exception {
                    dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart,
                        dicomModel, null, this));
                    // TODO add unique folder
                    File exportDir = AbstractProperties.buildAccessibleTempDirecotry("burn", "iso");
                    writeDicom(exportDir, tree);
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

    private void writeOther(File exportDir, ExportTree tree, String format) {

        boolean keepNames = true;
        int jpegQuality = 90;

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
                            buffer.append(makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                        }
                    } else {
                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
                        buffer.append(File.separator);
                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.StudyInstanceUID)));
                        buffer.append(File.separator);
                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                        instance = makeFileIDs(instance);
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
                        LOGGER.error("Cannot export DICOM file: ", format, img.getFile()); //$NON-NLS-1$
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

                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
                        buffer.append(File.separator);
                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.StudyInstanceUID)));
                        buffer.append(File.separator);
                        buffer.append(makeFileIDs((String) img.getTagValue(TagW.SeriesInstanceUID)));
                        iuid = makeFileIDs(iuid);

                        File destinationDir = new File(exportDir, buffer.toString());
                        boolean newSeries = destinationDir.mkdirs();

                        File destinationFile = new File(destinationDir, iuid);
                        if (FileUtil.nioCopyFile(img.getFile(), destinationFile)) {
                            if (writer != null) {
                                DicomInputStream in = new DicomInputStream(destinationFile);
                                in.setHandler(new StopTagInputHandler(Tag.PixelData));
                                DicomObject dcmobj = in.readDicomObject();
                                DicomObject patrec = dicomStruct.makePatientDirectoryRecord(dcmobj);
                                DicomObject styrec = dicomStruct.makeStudyDirectoryRecord(dcmobj);
                                DicomObject serrec = dicomStruct.makeSeriesDirectoryRecord(dcmobj);

                                // Icon Image Sequence (0088,0200).This Icon Image is representative of the Series. It
                                // may or may not correspond to one of the images of the Series.
                                if (newSeries && node.getParent() instanceof DefaultMutableTreeNode) {
                                    DicomImageElement midImage =
                                        ((DicomSeries) ((DefaultMutableTreeNode) node.getParent()).getUserObject())
                                            .getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
                                    DicomObject seq = mkIconItem(midImage);
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

    private static String toHex(int val) {
        char[] ch8 = new char[8];
        for (int i = 8; --i >= 0; val >>= 4) {
            ch8[i] = HEX_DIGIT[val & 0xf];
        }

        return String.valueOf(ch8);
    }

    private static String makeFileIDs(String uid) {
        return toHex(uid.hashCode());
    }

    private DicomObject mkIconItem(DicomImageElement image) {
        if (image == null) {
            return null;
        }
        BufferedImage thumbnail = null;
        PlanarImage imgPl = image.getImage(null);
        if (imgPl != null) {
            RenderedImage img = image.getRenderedImage(imgPl);
            final double scale = Math.min(128 / (double) img.getHeight(), 128 / (double) img.getWidth());
            final PlanarImage thumb =
                scale < 1.0 ? SubsampleAverageDescriptor.create(img, scale, scale, Thumbnail.DownScaleQualityHints)
                    .getRendering() : PlanarImage.wrapRenderedImage(img);
            thumbnail = thumb.getAsBufferedImage();
        }
        if (thumbnail == null) {
            return null;
        }
        int w = thumbnail.getWidth();
        int h = thumbnail.getHeight();

        String pmi = (String) image.getTagValue(TagW.PhotometricInterpretation);
        BufferedImage bi = thumbnail;
        if (thumbnail.getColorModel().getColorSpace().getType() != ColorSpace.CS_GRAY) {
            bi = convertBI(thumbnail, BufferedImage.TYPE_BYTE_INDEXED);
            pmi = "PALETTE COLOR"; //$NON-NLS-1$
        }

        byte[] iconPixelData = new byte[w * h];
        DicomObject iconItem = new BasicDicomObject();

        if ("PALETTE COLOR".equals(pmi)) { //$NON-NLS-1$
            IndexColorModel cm = (IndexColorModel) bi.getColorModel();
            int[] lutDesc = { cm.getMapSize(), 0, 8 };
            byte[] r = new byte[lutDesc[0]];
            byte[] g = new byte[lutDesc[0]];
            byte[] b = new byte[lutDesc[0]];
            cm.getReds(r);
            cm.getGreens(g);
            cm.getBlues(b);
            iconItem.putInts(Tag.RedPaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.putInts(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.putInts(Tag.BluePaletteColorLookupTableDescriptor, VR.US, lutDesc);
            iconItem.putBytes(Tag.RedPaletteColorLookupTableData, VR.OW, r, false);
            iconItem.putBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, g, false);
            iconItem.putBytes(Tag.BluePaletteColorLookupTableData, VR.OW, b, false);

            Raster raster = bi.getRaster();
            for (int y = 0, i = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x, ++i) {
                    iconPixelData[i] = (byte) raster.getSample(x, y, 0);
                }
            }
        } else {
            pmi = "MONOCHROME2"; //$NON-NLS-1$
            for (int y = 0, i = 0; y < h; ++y) {
                for (int x = 0; x < w; ++x, ++i) {
                    iconPixelData[i] = (byte) bi.getRGB(x, y);
                }
            }
        }
        iconItem.putString(Tag.PhotometricInterpretation, VR.CS, pmi);
        iconItem.putInt(Tag.Rows, VR.US, h);
        iconItem.putInt(Tag.Columns, VR.US, w);
        iconItem.putInt(Tag.SamplesPerPixel, VR.US, 1);
        iconItem.putInt(Tag.BitsAllocated, VR.US, 8);
        iconItem.putInt(Tag.BitsStored, VR.US, 8);
        iconItem.putInt(Tag.HighBit, VR.US, 7);
        iconItem.putBytes(Tag.PixelData, VR.OW, iconPixelData);
        return iconItem;
    }

    private BufferedImage convertBI(BufferedImage src, int imageType) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), imageType);
        Graphics2D big = dst.createGraphics();
        try {
            big.drawImage(src, 0, 0, null);
        } finally {
            big.dispose();
        }
        return dst;
    }

    public static boolean writeCompressedImageFromDICOM(DicomImageElement dicom, File outputFolder, String extension) {
        File file = new File(outputFolder, (String) dicom.getTagValue(TagW.SOPInstanceUID) + extension); //$NON-NLS-1$
        if (file.exists() && !file.canWrite()) {
            return false;
        }
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            DicomInputStream in = new DicomInputStream(dicom.getFile());
            DicomObject dcmObj = in.readDicomObject();
            DicomElement pixelDataDcmElement = dcmObj.get(Tag.PixelData);
            byte[] pixelData = pixelDataDcmElement.getFragment(1);
            os.write(pixelData);
        } catch (OutOfMemoryError e) {
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        } finally {
            FileUtil.safeClose(os);
        }
        return true;
    }

    private File makeISO(File exportDir) {
        boolean enableRockRidge = true;
        boolean enableJoliet = true;

        // Output file
        File outfile = new File(exportDir.getParent(), exportDir.getName() + ".iso");

        // Directory hierarchy, starting from the root
        ISO9660RootDirectory.MOVED_DIRECTORIES_STORE_NAME = "rr_moved";
        ISO9660RootDirectory root = new ISO9660RootDirectory();

        try {
            File[] files = exportDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.exists()) {
                        if (file.isDirectory()) {
                            root.addContentsRecursively(file);
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
}
