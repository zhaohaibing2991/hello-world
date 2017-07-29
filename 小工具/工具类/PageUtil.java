package com.book.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

public class PageUtil {
	
	public static void page(HttpServletRequest request, Integer page, Integer pageSize, String url, List list, Integer listCount) {
		page(request, page, pageSize, url, list, listCount, null);
	}
	
	public static void page(HttpServletRequest request, Integer page, Integer pageSize, String url, List list, Integer listCount, Map searchMap) {
		String search = "";
		if(searchMap != null) {
			Set keySet = searchMap.keySet();
			Iterator iterator = keySet.iterator();
			while(iterator.hasNext()) {
				Object key = iterator.next();
				Object value = searchMap.get(key);
				if(value != null && !value.equals("")) {
					search += "&" + key + "=" + value;
				}
			}
		}
		
		
		int pageCount = listCount / pageSize + (listCount % pageSize == 0 ? 0 : 1);
		
		String flag = url.indexOf("?") == -1 ? "?" : "&";
		
		String[] pageLink = new String[4];
		
		if(page == 1) {
			pageLink[0] = "首页";
		} else {
			pageLink[0] = "<a href='"+request.getContextPath() + url + flag + "page=1"+search+"'>首页</a>";
		}
		
		if(page-1 <= 0) {
			pageLink[1] = "上一页";
		} else {
			pageLink[1] = "<a href='"+request.getContextPath()+url + flag + "page="+(page-1)+""+search+"'>上一页</a>";
		}
		
		if(page + 1 > pageCount) {
			pageLink[2] = "下一页";
		} else {
			pageLink[2] = "<a href='"+request.getContextPath() + url + flag + "page="+(page+1)+""+search+"'>下一页</a>";
		}
		
		if(page == pageCount) {
			pageLink[3] = "末页";
		} else {
			pageLink[3] = "<a href='"+request.getContextPath() + url + flag + "page="+pageCount+""+search+"'>末页</a>";
		}
		
		
		request.setAttribute("list", list);
		request.setAttribute("page", page);
		request.setAttribute("pageSize", pageSize);
		request.setAttribute("listCount", listCount);
		request.setAttribute("pageCount", pageCount);
		request.setAttribute("firstPage", pageLink[0]);
		request.setAttribute("prevPage", pageLink[1]);
		request.setAttribute("nextPage", pageLink[2]);
		request.setAttribute("lastPage", pageLink[3]);
	}
}
