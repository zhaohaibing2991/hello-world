package com.ztools.resmgr.actions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypes;
import org.apache.velocity.context.Context;
import org.json.JSONArray;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ztools.beans.ZBean;
import com.ztools.resmgr.beans.Picture;
import com.ztools.resmgr.beans.UploadFile;
import com.ztools.resmgr.dba.FileHandler;
import com.ztools.resmgr.dba.ImageHandler;
import com.ztools.vms.Configure;
import com.ztools.vms.actions.AbsAction;
import com.ztools.exceptions.ExceptionPrinter;

public class Ap extends AbsAction {
  private Logger logger = LogManager.getLogger(Ap.class);

  private static final String PROP_IMGHOST = "cfg.string.z-resmgr.imghost";
  private static final String PROP_IMGBASE = "cfg.string.z-resmgr.imgbase";
  private static final String PROP_DOWNURL_PREFIX =
    "cfg.string.z-resmgr.download_url_prefix";
  private static final String PROP_UPLOAD_DIR =
    "cfg.string.z-resmgr.upload_directory";
  private static final String REQ_SCOPE = "scope";
  private static final String REQ_TMPUPLOADID = "tmpUploadId";
  private static final String REQ_TARGETFILEID = "tfId";
  private static final String SLASH = "/";

  private FileHandler fileHandler = new FileHandler();
  private ImageHandler imgHandler = new ImageHandler();

  private static Map<String, UploadFile> uploadSessionMap = new HashMap<>();

  private UploadFile findFileInSession(final String tmpUploadId,
                                       final FileItem item,
                                       String fileScope,
                                       final long totalSize,
                                       final String targetFileId) {
    String sessionKey = tmpUploadId + "-" + item.getName();
    UploadFile file = uploadSessionMap.get(sessionKey);

    if (isEmpty(fileScope)) {
      fileScope = ZBean.class.getSimpleName() + SLASH
        + item.getContentType() + "s";
    }

    if (null == file) {
      fileScope = SLASH + fileScope + SLASH;
      // 判断是否是图片，根据类型创建对应的文件，如果有指定目标ID，则
      // 设置文件的对象的为对应的ID值，这个值就是实际的文件名。在服务
      // 器端采用统一的ID规格来命名文件不会使用原文件名，原文件名保存
      // 在文件信息库中。
      boolean isImage = !isEmpty(item.getContentType())
            && item.getContentType().startsWith("image/");
      file = isImage ? new Picture() : new UploadFile();
      if (null != targetFileId) file.setId(targetFileId);

      String dir = Configure.getValueByProp(PROP_DOWNURL_PREFIX) + fileScope;
      //+ item.getContentType() + "s/";


      String[] fileNameArr = item.getName().split("[.]");
      String extension = (1 < fileNameArr.length ?
                          "." + fileNameArr[fileNameArr.length - 1] : "");
      File directory = new File(Configure.getValueByProp(PROP_UPLOAD_DIR)
                                + fileScope); // + item.getContentType() + "s");
      directory.mkdirs();
      logger.debug("file will save to: " + directory + "/"
                   + file.getId() + extension
                   + " "+ item.getSize());
      file.setType(item.getContentType());
      file.setName(item.getName());
      file.setRealName(file.getId() + extension);
      file.setSize(totalSize);
      file.setUrl(dir + file.getId() + extension);
      file.setDeleteUrl(file.getUrl());
      file.setDeleteType("DELETE");
      file.setExtension(extension);
      //file.setCover(isCover);
      file.setAbsolutePath(Configure.getValueByProp(PROP_UPLOAD_DIR)
                           + fileScope // + item.getContentType() + "s/"
                           + file.getId() + extension);

      if (file instanceof Picture) {
        Picture p = (Picture) file;
        // ??: p.setDir(item.getContentType() + S + "/");
        p.setThumbnailUrl(dir + file.getId() + "_s" + extension);
        p.setMiddleThumbnaiUrl(dir + file.getId() + "_m" + extension);
        p.setLargeThumbnaiUrl(dir + file.getId() + "_l" + extension);
        //isSaved = imgHandler.create(p, item.getInputStream());
        file = p;
      }

      uploadSessionMap.put(sessionKey, file);
    }

    return file;
  }

  public static void tikaParse(InputStream in) {
    Metadata metadata = new Metadata();
    MimeTypes mimeTypes = TikaConfig.getDefaultConfig().getMimeRepository();
    try {
      String mime = mimeTypes.detect(in, metadata).toString();
      //System.out.println("mime: " + mime);
    } catch (Exception e) {
      com.ztools.exceptions.ExceptionPrinter.printStackTrace(e);
    }
  }

  private void initHeaders(HttpServletRequest request) {
    // wrap httpheader
    String accept = request.getHeader("Accept");
    System.out.println("accept: " + accept);
    if (null != accept && accept.contains("application/json")) {
      this.addHeader("Content-Type", "application/json");
      this.addHeader("X-Content-Type-Options", "nosniff");
      this.addHeader("Access-Control-Allow-Origin", "*");
      this.addHeader("Access-Control-Allow-Methods",
                     "OPTIONS, HEAD, GET, POST, PUT, DELETE");
      this.addHeader("Access-Control-Allow-Headers",
                     "Content-Type, Content-Range, Content-Disposition");
    }
  }

  // 这个是块上传，能支持断点续传
  public void uploadChunk(HttpServletRequest request, Context context) {

    String scope = request.getParameter(REQ_SCOPE);
    String tmpUploadId = request.getParameter(REQ_TMPUPLOADID);
    String targetFileId = request.getParameter(REQ_TARGETFILEID);

    // wrap httpheader
    initHeaders(request);

    List<UploadFile> files = new ArrayList<>();
    String contentType = request.getHeader("Content-Type");
    // 文件分块信息
    String contentRange = request.getHeader("Content-Range");
    // 希望保存的文件
    String contentDisposition = request.getHeader("Content-Disposition");
    if (null != contentDisposition
        && ServletFileUpload.isMultipartContent(request)) {
      String fileNames =
        contentDisposition.substring("attachment; filename=".length());
      String filename = fileNames.substring(fileNames.indexOf("\"")
                                            + 1, fileNames.lastIndexOf("\""));

      //rafile = new RandomAccessFile(uploadFile,"rw");
      if(contentRange == null){
        // throw new RuntimeException("Not chunk upload!");
        return ;
      }
      String totalSizeStr = contentRange.substring(contentRange.lastIndexOf("/") + 1);
      long totalSize = Long.parseLong(totalSizeStr);

      DiskFileItemFactory factory = new DiskFileItemFactory();
      ServletFileUpload upload = new ServletFileUpload(factory);
      logger.debug("chunkupload size limit: [" + targetFileId + "] "
                   + factory.getSizeThreshold() + " "
                   + upload.getFileSizeMax() + " " + upload.getSizeMax());

      try {
        RandomAccessFile raFile = null;
        List<FileItem> fileList = upload.parseRequest(request);
        //System.out.println("upload file count: " + fileList.size());

        for (FileItem item : fileList) {
          UploadFile file = findFileInSession(tmpUploadId, item,
                                              scope, totalSize,
                                              targetFileId);
          try {
            boolean isSaved = false;
            // 创建一个随机访问文件，用来接收上传的文件块
            raFile = new RandomAccessFile(new File(file.getAbsolutePath()), "rw");
            //System.out.println("raFile: " + raFile.length() + " " +
            //item.get().length + " / " + totalSize);
            if(raFile.length() < totalSize){
              raFile.seek(raFile.length());
              raFile.write(item.get());
            }

            // fileHandler.create第二个参数是空，也就是说只会保存文件
            // 索引，不会写实际的文件，文件已经在上面的代码中保存了
            if (raFile.length() >= totalSize)
              isSaved = saveFile(file, null);

            if (isSaved) {
              files.add(file);
            }
          } catch (IOException e) {
            com.ztools.exceptions.ExceptionPrinter.printStackTrace(e);
          }
        }


      } catch (FileUploadException e) {
        com.ztools.exceptions.ExceptionPrinter.printStackTrace(e);
      }

    } else {
      // System.out.println("text........................");
      setContext(request, context);
      return ;
    }

    context.put("responseText", "{\"files\": "
                + new JSONArray(files).toString() + "}");
  }

  @Override
  public void setContext(HttpServletRequest request, Context context) {

    String scope = request.getParameter(REQ_SCOPE);
    String tmpUploadId = request.getParameter(REQ_TMPUPLOADID);
    String targetFileId = request.getParameter(REQ_TARGETFILEID);

    // wrap httpheader
    initHeaders(request);

    List<UploadFile> files = new ArrayList<>();
    String contextType = request.getHeader("Content-Type");
    String contentRange = request.getHeader("Content-Range");
    if (null != contextType && contextType.contains("multipart/form-data")) {
      DiskFileItemFactory factory = new DiskFileItemFactory();
      ServletFileUpload upload = new ServletFileUpload(factory);
      logger.debug("commonupload size limit: " +
                   factory.getSizeThreshold() + " "
                   + upload.getFileSizeMax() + " "
                   + upload.getSizeMax());
      try {
        List<FileItem> fileList = upload.parseRequest(request);
        System.out.println("upload file count: " + fileList.size());
        for (FileItem item : fileList) {
          UploadFile file = findFileInSession(tmpUploadId, item, scope,
                                              item.getSize(), targetFileId);
          try {
            boolean isSaved = saveFile(file, item.getInputStream());
            if (isSaved) {
              files.add(file);
              //dba.create(file);
            }
          } catch (IOException e) {
            logger.debug(e);
            //e.printStackTrace();
          }

        }

        context.put("responseText", "{\"files\": "
                    + new JSONArray(files).toString() + "}");
      } catch (FileUploadException e) {
        com.ztools.exceptions.ExceptionPrinter.printStackTrace(e);
      }
    }

  }

  private boolean saveFile(UploadFile file, InputStream in) {

    // if (file instanceof Picture) {
      // imgHandler.create(file, in);
    // } else {
      // fileHandler.create(file, in);
    // }

    return fileHandler.create(file, in);
  }
}
