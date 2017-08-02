package com.ztools.resmgr.actions;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.velocity.context.Context;

import com.ztools.beans.ObjectId;
import com.ztools.resmgr.beans.UploadFile;
import com.ztools.resmgr.dba.FileHandler;
import com.ztools.vms.Configure;
import com.ztools.vms.actions.AbsAction;
import com.ztools.vms.dba.DBAccess;
import com.ztools.vms.dba.DBManager;

public class Upload extends AbsAction {
  @Override
  public void setContext(HttpServletRequest request, Context context) {
    DBAccess dba = DBManager.fetchDBAccess(Configure
        .getValueByProp(FileHandler.KEY_CUSTOM_FILE_INDEX_DBNAME));
    UploadFile query = new UploadFile();
    List<Object> list = dba.find(query);
    System.out.println("list:" + list.size());

    // 生成一个ID，用来标识requestid，上传文件时可以用来防止名称重复问题
    ObjectId tmpUploadId = new ObjectId();
    context.put("fileList", list);
    context.put("tmpUploadId", tmpUploadId.toString());
  }
}
