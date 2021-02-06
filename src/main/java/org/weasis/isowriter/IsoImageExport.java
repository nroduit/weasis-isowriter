/*
 * Copyright (c) 2016 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.isowriter;

import com.github.stephenc.javaisotools.iso9660.ConfigException;
import com.github.stephenc.javaisotools.iso9660.ISO9660RootDirectory;
import com.github.stephenc.javaisotools.iso9660.impl.CreateISO;
import com.github.stephenc.javaisotools.iso9660.impl.ISO9660Config;
import com.github.stephenc.javaisotools.iso9660.impl.ISOImageFileHandler;
import com.github.stephenc.javaisotools.joliet.impl.JolietConfig;
import com.github.stephenc.javaisotools.rockridge.impl.RockRidgeConfig;
import com.github.stephenc.javaisotools.sabre.HandlerException;
import com.github.stephenc.javaisotools.sabre.StreamHandler;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
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
import org.dcm4che3.util.UIDUtils;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.FileExtractor;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomDirLoader;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.ExportDicom;
import org.weasis.dicom.explorer.ExportTree;
import org.weasis.dicom.explorer.pr.DicomPrSerializer;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

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
    JPanel panel = new JPanel();

    add(panel, BorderLayout.NORTH);
    GridBagLayout gblPanel = new GridBagLayout();

    panel.setLayout(gblPanel);

    GridBagConstraints gbccheckBoxAddWeasisViewer = new GridBagConstraints();
    gbccheckBoxAddWeasisViewer.anchor = GridBagConstraints.NORTHWEST;
    gbccheckBoxAddWeasisViewer.insets = new Insets(0, 0, 0, 5);
    gbccheckBoxAddWeasisViewer.gridx = 0;
    gbccheckBoxAddWeasisViewer.gridy = 0;
    panel.add(checkBoxAddWeasisViewer, gbccheckBoxAddWeasisViewer);

    GridBagConstraints gbcCheckBoxAddJpeg = new GridBagConstraints();
    gbcCheckBoxAddJpeg.insets = new Insets(0, 0, 0, 5);
    gbcCheckBoxAddJpeg.anchor = GridBagConstraints.NORTHWEST;
    gbcCheckBoxAddJpeg.gridx = 1;
    gbcCheckBoxAddJpeg.gridy = 0;
    panel.add(checkBoxAddJpeg, gbcCheckBoxAddJpeg);

    GridBagConstraints gbcHorizontalStrut = new GridBagConstraints();
    gbcHorizontalStrut.weightx = 1.0;
    gbcHorizontalStrut.gridx = 2;
    gbcHorizontalStrut.gridy = 0;
    panel.add(horizontalStrut, gbcHorizontalStrut);

    GridBagConstraints gbccheckBoxCompression = new GridBagConstraints();
    gbccheckBoxCompression.anchor = GridBagConstraints.NORTHWEST;
    gbccheckBoxCompression.insets = new Insets(0, 0, 5, 5);
    gbccheckBoxCompression.gridx = 0;
    gbccheckBoxCompression.gridy = 1;
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
    // Do nothing
  }

  protected void updateChanges() {
    // Do nothing
  }

  @Override
  public void closeAdditionalWindow() {
    applyChange();
  }

  @Override
  public void resetoDefaultValues() {
    // Do nothing
  }

  @Override
  public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {
    browseImgFile();
    if (outputFile != null) {
      final File exportFile = outputFile.getCanonicalFile();
      ExplorerTask task =
          new ExplorerTask("Exporting...", false) {

            @Override
            protected Boolean doInBackground() throws Exception {
              dicomModel.firePropertyChange(
                  new ObservableEvent(
                      ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
              File exportDir =
                  FileUtil.createTempDir(AppProperties.buildAccessibleTempDirectory("tmp", "burn"));
              writeDicom(this, exportDir, model);
              if (checkBoxAddJpeg.isSelected()) {
                writeJpeg(this, new File(exportDir, "JPEG"), model, true, 90);
              }
              if (checkBoxAddWeasisViewer.isSelected()) {
                URL url =
                    ResourceUtil.getResourceURL("lib/weasis-distributions.zip", this.getClass());
                if (url == null) {
                  LOGGER.error("Cannot find the embedded portable distribution");
                } else {
                  FileUtil.unzip(url.openStream(), exportDir);
                }
              }
              if (this.isCancelled()) {
                return false;
              }
              makeISO(exportDir, exportFile, true, true);

              return true;
            }

            @Override
            protected void done() {
              Properties pref = ExportIsoFactory.EXPORT_PERSISTENCE;
              pref.setProperty(ADD_JPEG, String.valueOf(checkBoxAddJpeg.isSelected()));
              pref.setProperty(ADD_VIEWER, String.valueOf(checkBoxAddWeasisViewer.isSelected()));

              dicomModel.firePropertyChange(
                  new ObservableEvent(
                      ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
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
    File file;
    if (fileChooser.showSaveDialog(this) != 0 || (file = fileChooser.getSelectedFile()) == null) {
      outputFile = null;
      return;
    } else {
      outputFile = file;
      ExportIsoFactory.EXPORT_PERSISTENCE.setProperty(LAST_FOLDER, file.getParent());
    }
  }

  private static String getinstanceFileName(MediaElement img) {
    Integer instance = TagD.getTagValue(img, Tag.InstanceNumber, Integer.class);
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
    return TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
  }

  private void writeJpeg(
      ExplorerTask task, File exportDir, CheckTreeModel model, boolean keepNames, int jpegQuality) {

    try {
      synchronized (exportTree) {
        ArrayList<String> seriesGph = new ArrayList<>();
        TreePath[] paths = model.getCheckingPaths();
        for (TreePath treePath : paths) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
          if (node.getUserObject() instanceof Series) {
            MediaSeries<?> s = (MediaSeries<?>) node.getUserObject();
            if (LangUtil.getNULLtoFalse((Boolean) s.getTagValue(TagW.ObjectToSave))) {
              Series<?> series = (Series<?>) s.getTagValue(CheckTreeModel.SourceSeriesForPR);
              if (series != null) {
                seriesGph.add((String) series.getTagValue(TagD.get(Tag.SeriesInstanceUID)));
              }
            }
          }
        }

        for (TreePath treePath : paths) {
          if (task.isCancelled()) {
            return;
          }

          DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

          if (node.getUserObject() instanceof DicomImageElement) {
            DicomImageElement img = (DicomImageElement) node.getUserObject();
            // Get instance number instead SOPInstanceUID to handle multiframe
            String instance = getinstanceFileName(img);
            if (!keepNames) {
              instance = makeFileIDs(instance);
            }
            String path = buildPath(img, keepNames, node);
            File destinationDir = new File(exportDir, path);
            destinationDir.mkdirs();

            PlanarImage image = img.getImage(null);
            if (image != null) {
              image = img.getRenderedImage(image);
            }
            if (image != null) {
              File destinationFile = new File(destinationDir, instance + ".jpg"); // $NON-NLS-1$

              MatOfInt map = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality);
              ImageProcessor.writeImage(image.toMat(), destinationFile, map);
            } else {
              LOGGER.error(
                  "Cannot export DICOM file to jpeg: {}", //$NON-NLS-1$
                  img.getFileCache().getOriginalFile().orElse(null));
            }

            // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
            img.removeImageFromCache();
          } else if (node.getUserObject() instanceof MediaElement
              && node.getUserObject() instanceof FileExtractor) {
            MediaElement dcm = (MediaElement) node.getUserObject();
            File fileSrc = ((FileExtractor) dcm).getExtractFile();
            if (fileSrc != null) {
              // Get instance number instead SOPInstanceUID to handle multiframe
              String instance = getinstanceFileName(dcm);
              if (!keepNames) {
                instance = makeFileIDs(instance);
              }
              String path = buildPath(dcm, keepNames, node);
              File destinationDir = new File(exportDir, path);
              destinationDir.mkdirs();

              File destinationFile =
                  new File(destinationDir, instance + FileUtil.getExtension(fileSrc.getName()));
              FileUtil.nioCopyFile(fileSrc, destinationFile);
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Cannot extract media from DICOM", e); // $NON-NLS-1$
    }
  }

  private void writeDicom(ExplorerTask task, File exportDir, CheckTreeModel model)
      throws IOException {
    boolean keepNames = false;
    boolean writeDicomdir = true;
    boolean cdCompatible = true;

    File writeDir = exportDir;

    DicomDirWriter writer = null;
    try {

      if (writeDicomdir) {
        File dcmdirFile = new File(writeDir, "DICOMDIR"); // $NON-NLS-1$
        writer = DicomDirLoader.open(dcmdirFile);
      }

      synchronized (exportTree) {
        ArrayList<String> uids = new ArrayList<>();
        TreePath[] paths = model.getCheckingPaths();
        for (TreePath treePath : paths) {
          if (task.isCancelled()) {
            return;
          }

          DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

          if (node.getUserObject() instanceof DicomImageElement) {
            DicomImageElement img = (DicomImageElement) node.getUserObject();
            String iuid = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
            int index = uids.indexOf(iuid);
            if (index == -1) {
              uids.add(iuid);
            } else {
              // Write only once the file for multiframe
              continue;
            }
            if (!keepNames) {
              iuid = makeFileIDs(iuid);
            }

            String path = buildPath(img, keepNames, writeDicomdir, cdCompatible, node);
            File destinationDir = new File(writeDir, path);
            destinationDir.mkdirs();

            File destinationFile = new File(destinationDir, iuid);
            if (img.saveToFile(destinationFile)) {
              writeInDicomDir(writer, img, node, iuid, destinationFile);
            } else {
              LOGGER.error(
                  "Cannot export DICOM file: {}", //$NON-NLS-1$
                  img.getFileCache().getOriginalFile().orElse(null));
            }
          } else if (node.getUserObject() instanceof MediaElement) {
            MediaElement dcm = (MediaElement) node.getUserObject();
            String iuid = TagD.getTagValue(dcm, Tag.SOPInstanceUID, String.class);
            if (!keepNames) {
              iuid = makeFileIDs(iuid);
            }

            String path = buildPath(dcm, keepNames, writeDicomdir, cdCompatible, node);
            File destinationDir = new File(writeDir, path);
            destinationDir.mkdirs();

            File destinationFile = new File(destinationDir, iuid);
            if (dcm.saveToFile(destinationFile)) {
              writeInDicomDir(writer, dcm, node, iuid, destinationFile);
            }
          } else if (node.getUserObject() instanceof Series) {
            MediaSeries<?> s = (MediaSeries<?>) node.getUserObject();
            if (LangUtil.getNULLtoFalse((Boolean) s.getTagValue(TagW.ObjectToSave))) {
              Series<?> series = (Series<?>) s.getTagValue(CheckTreeModel.SourceSeriesForPR);
              if (series != null) {
                String seriesInstanceUID = UIDUtils.createUID();
                for (MediaElement dcm : series.getMedias(null, null)) {
                  GraphicModel grModel = (GraphicModel) dcm.getTagValue(TagW.PresentationModel);
                  if (grModel != null && grModel.hasSerializableGraphics()) {
                    String path = buildPath(dcm, keepNames, writeDicomdir, cdCompatible, node);
                    buildAndWritePR(
                        dcm, keepNames, new File(writeDir, path), writer, node, seriesInstanceUID);
                  }
                }
              }
            }
          }
        }
      }
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      LOGGER.error("Cannot export DICOM", e); // $NON-NLS-1$
    } finally {
      if (writer != null) {
        // Commit DICOMDIR changes and close the file
        writer.close();
      }
    }
  }

  public static Attributes buildAndWritePR(
      MediaElement img,
      boolean keepNames,
      File destinationDir,
      DicomDirWriter writer,
      DefaultMutableTreeNode node,
      String seriesInstanceUID) {
    Attributes imgAttributes =
        img.getMediaReader() instanceof DcmMediaReader
            ? ((DcmMediaReader) img.getMediaReader()).getDicomObject()
            : null;
    if (imgAttributes != null) {
      GraphicModel grModel = (GraphicModel) img.getTagValue(TagW.PresentationModel);
      if (grModel != null && grModel.hasSerializableGraphics()) {
        String prUid = UIDUtils.createUID();
        File outputFile = new File(destinationDir, keepNames ? prUid : makeFileIDs(prUid));
        destinationDir.mkdirs();
        Attributes prAttributes =
            DicomPrSerializer.writePresentation(
                grModel, imgAttributes, outputFile, seriesInstanceUID, prUid);
        if (prAttributes != null) {
          try {
            writeInDicomDir(writer, prAttributes, node, outputFile.getName(), outputFile);
          } catch (IOException e) {
            LOGGER.error("Writing DICOMDIR", e); // $NON-NLS-1$
          }
        }
      }
    }
    return imgAttributes;
  }

  public static String buildPath(
      MediaElement img,
      boolean keepNames,
      boolean writeDicomdir,
      boolean cdCompatible,
      DefaultMutableTreeNode node) {
    StringBuilder buffer = new StringBuilder();
    // Cannot keep folders names with DICOMDIR (could be not valid)
    if (keepNames && !writeDicomdir) {
      TreeNode[] objects = node.getPath();
      if (objects.length > 2) {
        for (int i = 1; i < objects.length - 1; i++) {
          buffer.append(buildFolderName(objects[i].toString(), 30));
          buffer.append(File.separator);
        }
      }
    } else {
      if (cdCompatible) {
        buffer.append("DICOM"); // $NON-NLS-1$
        buffer.append(File.separator);
      }
      buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
      buffer.append(File.separator);
      buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.StudyInstanceUID, String.class)));
      buffer.append(File.separator);
      buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
    }
    return buffer.toString();
  }

  public static String buildPath(MediaElement img, boolean keepNames, DefaultMutableTreeNode node) {
    StringBuilder buffer = new StringBuilder();
    if (keepNames) {
      TreeNode[] objects = node.getPath();
      if (objects.length > 3) {
        buffer.append(buildFolderName(objects[1].toString(), 30));
        buffer.append(File.separator);
        buffer.append(buildFolderName(objects[2].toString(), 30));
        buffer.append(File.separator);
        buffer.append(buildFolderName(objects[3].toString(), 25));
        buffer.append('-');
        // Hash of UID to guaranty the unique behavior of the name.
        buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
      }
    } else {
      buffer.append(makeFileIDs((String) img.getTagValue(TagW.PatientPseudoUID)));
      buffer.append(File.separator);
      buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.StudyInstanceUID, String.class)));
      buffer.append(File.separator);
      buffer.append(makeFileIDs(TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class)));
    }
    return buffer.toString();
  }

  private static String buildFolderName(String str, int length) {
    String value = FileUtil.getValidFileNameWithoutHTML(str);
    return StringUtil.getTruncatedString(value, length, Suffix.NO);
  }

  private static boolean writeInDicomDir(
      DicomDirWriter writer,
      MediaElement img,
      DefaultMutableTreeNode node,
      String iuid,
      File destinationFile)
      throws IOException {
    if (writer != null) {
      if (!(img.getMediaReader() instanceof DcmMediaReader)
          || ((DcmMediaReader) img.getMediaReader()).getDicomObject() == null) {
        LOGGER.error(
            "Cannot export DICOM file: ",
            img.getFileCache().getOriginalFile().orElse(null)); // $NON-NLS-1$
        return false;
      }
      return writeInDicomDir(
          writer,
          ((DcmMediaReader) img.getMediaReader()).getDicomObject(),
          node,
          iuid,
          destinationFile);
    }
    return false;
  }

  private static boolean writeInDicomDir(
      DicomDirWriter writer,
      Attributes dataset,
      DefaultMutableTreeNode node,
      String iuid,
      File destinationFile)
      throws IOException {
    if (writer != null && dataset != null) {
      Attributes fmi = dataset.createFileMetaInformation(UID.ImplicitVRLittleEndian);

      String miuid = fmi.getString(Tag.MediaStorageSOPInstanceUID, null);

      String pid = dataset.getString(Tag.PatientID, null);
      String styuid = dataset.getString(Tag.StudyInstanceUID, null);
      String seruid = dataset.getString(Tag.SeriesInstanceUID, null);

      if (styuid != null && seruid != null) {
        if (pid == null) {
          pid = styuid;
          dataset.setString(Tag.PatientID, VR.LO, pid);
        }
        Attributes patRec = writer.findPatientRecord(pid);
        if (patRec == null) {
          patRec =
              DicomDirLoader.RecordFactory.createRecord(
                  RecordType.PATIENT, null, dataset, null, null);
          writer.addRootDirectoryRecord(patRec);
        }
        Attributes studyRec = writer.findStudyRecord(patRec, styuid);
        if (studyRec == null) {
          studyRec =
              DicomDirLoader.RecordFactory.createRecord(
                  RecordType.STUDY, null, dataset, null, null);
          writer.addLowerDirectoryRecord(patRec, studyRec);
        }
        Attributes seriesRec = writer.findSeriesRecord(studyRec, seruid);
        if (seriesRec == null) {
          seriesRec =
              DicomDirLoader.RecordFactory.createRecord(
                  RecordType.SERIES, null, dataset, null, null);
          /*
           * Icon Image Sequence (0088,0200).This Icon Image is representative of the Series. It may or may
           * not correspond to one of the images of the Series.
           */
          if (seriesRec != null && node.getParent() instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) node.getParent()).getUserObject();
            if (userObject instanceof DicomSeries) {
              DicomImageElement midImage =
                  ((DicomSeries) userObject)
                      .getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
              Attributes iconItem = mkIconItem(midImage);
              if (iconItem != null) {
                seriesRec.newSequence(Tag.IconImageSequence, 1).add(iconItem);
              }
            }
          }
          writer.addLowerDirectoryRecord(studyRec, seriesRec);
        }
        Attributes instRec;
        if (writer.findLowerInstanceRecord(seriesRec, false, iuid) == null) {
          instRec =
              DicomDirLoader.RecordFactory.createRecord(
                  dataset, fmi, writer.toFileIDs(destinationFile));
          writer.addLowerDirectoryRecord(seriesRec, instRec);
        }
      } else {
        if (writer.findRootInstanceRecord(false, miuid) == null) {
          Attributes instRec =
              DicomDirLoader.RecordFactory.createRecord(
                  dataset, fmi, writer.toFileIDs(destinationFile));
          writer.addRootDirectoryRecord(instRec);
        }
      }
    }
    return true;
  }

  public static String makeFileIDs(String uid) {
    if (uid != null) {
      return Integer.toHexString(uid.hashCode());
    }
    return null;
  }

  private File makeISO(
      File exportDir, File exportFile, boolean enableRockRidge, boolean enableJoliet) {
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
      LOGGER.error("Error when adding files to ISO", e);
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
        jolietConfig.forceDotDelimiter(false);
      }

      // Create ISO
      StreamHandler streamHandler = new ISOImageFileHandler(outfile);
      CreateISO iso = new CreateISO(streamHandler, root);
      iso.process(iso9660Config, rrConfig, jolietConfig, null);
      return outfile;

    } catch (ConfigException | HandlerException | FileNotFoundException e) {
      LOGGER.error("Error when building ISO", e);
    } finally {
      FileUtil.recursiveDelete(exportDir);
    }
    return null;
  }

  public static Attributes mkIconItem(DicomImageElement image) {
    if (image == null) {
      return null;
    }
    PlanarImage thumbnail = null;
    PlanarImage imgPl = image.getImage(null);
    if (imgPl != null) {
      PlanarImage img = image.getRenderedImage(imgPl);
      thumbnail = ImageProcessor.buildThumbnail(img, new Dimension(128, 128), true);
    }
    // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
    image.removeImageFromCache();

    if (thumbnail == null) {
      return null;
    }
    int w = thumbnail.width();
    int h = thumbnail.height();

    String pmi = TagD.getTagValue(image, Tag.PhotometricInterpretation, String.class);
    if (thumbnail.channels() >= 3) {

      pmi = "PALETTE COLOR"; // $NON-NLS-1$
    }

    byte[] iconPixelData = new byte[w * h];
    Attributes iconItem = new Attributes();

    if ("PALETTE COLOR".equals(pmi)) { // $NON-NLS-1$
      BufferedImage bi =
          ImageConversion.convertTo(
              ImageConversion.toBufferedImage(thumbnail), BufferedImage.TYPE_BYTE_INDEXED);
      IndexColorModel cm = (IndexColorModel) bi.getColorModel();
      int[] lutDesc = {cm.getMapSize(), 0, 8};
      byte[] r = new byte[lutDesc[0]];
      byte[] g = new byte[lutDesc[0]];
      byte[] b = new byte[lutDesc[0]];
      cm.getReds(r);
      cm.getGreens(g);
      cm.getBlues(b);
      iconItem.setInt(Tag.RedPaletteColorLookupTableDescriptor, VR.US, lutDesc);
      iconItem.setInt(Tag.GreenPaletteColorLookupTableDescriptor, VR.US, lutDesc);
      iconItem.setInt(Tag.BluePaletteColorLookupTableDescriptor, VR.US, lutDesc);
      iconItem.setBytes(Tag.RedPaletteColorLookupTableData, VR.OW, r);
      iconItem.setBytes(Tag.GreenPaletteColorLookupTableData, VR.OW, g);
      iconItem.setBytes(Tag.BluePaletteColorLookupTableData, VR.OW, b);

      Raster raster = bi.getRaster();
      for (int y = 0, i = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x, ++i) {
          iconPixelData[i] = (byte) raster.getSample(x, y, 0);
        }
      }
    } else {
      pmi = "MONOCHROME2"; // $NON-NLS-1$
      thumbnail.get(0, 0, iconPixelData);
    }
    iconItem.setString(Tag.PhotometricInterpretation, VR.CS, pmi);
    iconItem.setInt(Tag.Rows, VR.US, h);
    iconItem.setInt(Tag.Columns, VR.US, w);
    iconItem.setInt(Tag.SamplesPerPixel, VR.US, 1);
    iconItem.setInt(Tag.BitsAllocated, VR.US, 8);
    iconItem.setInt(Tag.BitsStored, VR.US, 8);
    iconItem.setInt(Tag.HighBit, VR.US, 7);
    iconItem.setBytes(Tag.PixelData, VR.OW, iconPixelData);
    return iconItem;
  }
}
