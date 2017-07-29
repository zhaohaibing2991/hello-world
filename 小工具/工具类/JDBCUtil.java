package com.book.utils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

public class JDBCUtil {
	
	private static Logger logger = Logger.getLogger(JDBCUtil.class);
	
	private static String driver;
	private static String url;
	private static String user;
	private static String password;
	
	static {
		try {
			InputStream is = JDBCUtil.class.getResourceAsStream("/jdbc.properties");
			Properties prop = new Properties();
			prop.load(is);
			
			driver = prop.getProperty("driver");
			url = prop.getProperty("url");
			user = prop.getProperty("user");
			password = prop.getProperty("password");
			
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			Class.forName(driver);
		} catch (ClassNotFoundException e) {
			logger.error("没有找到Mysql驱动包。");
		}
	}
	
	private static Connection getConn() {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url, user, password);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return conn;
	}
	
	public static List getList(Class clazz, String sql) {
		List list = new ArrayList();
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			conn = getConn();
			
			ps = conn.prepareStatement(sql);
			
			rs = ps.executeQuery();
			
			ResultSetMetaData metaData = rs.getMetaData();
			
			Method[] methods = clazz.getDeclaredMethods();
			
			while(rs.next()) {
				Object obj = clazz.newInstance();
				for (int i = 1; i <= metaData.getColumnCount(); i++) {
					for (Method method : methods) {
						if(method.getName().startsWith("set")) {
							String name = method.getName().substring(3).toLowerCase();
							if(name.equalsIgnoreCase(metaData.getColumnName(i))){
								method.invoke(obj, rs.getObject(i));
							}
						}

					}
				}
				list.add(obj);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(rs, ps, conn);
		}
		return list;
	}
	
	
	public static int getListCount(String sql) {
		int result = 0;
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			conn = getConn();
			
			ps = conn.prepareStatement(sql);
			
			rs = ps.executeQuery();
			
			rs.next();
			
			result = rs.getInt(1);
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(rs, ps, conn);
		}
		return result;
	}
	
	
	public static Object getObjectById(Class clazz, String sql) {
		Object obj = null;
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			conn = getConn();
			
			ps = conn.prepareStatement(sql);
			
			rs = ps.executeQuery();
			
			ResultSetMetaData metaData = rs.getMetaData();
			
			Method[] methods = clazz.getDeclaredMethods();
			
			if(rs.next()) {
				obj = clazz.newInstance();
				for (int i = 1; i <= metaData.getColumnCount(); i++) {
					for (Method method : methods) {
						if(method.getName().startsWith("set")) {
							String name = method.getName().substring(3).toLowerCase();
							if(name.equalsIgnoreCase(metaData.getColumnName(i))) {
								method.invoke(obj, rs.getObject(i));
							}
						}

					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(rs, ps, conn);
		}
		return obj;
	}

	public static Number getMaxId(String sql) {
		Number num = null;
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			conn = getConn();
			
			ps = conn.prepareStatement(sql);
			
			rs = ps.executeQuery();
			
			if(rs.next()) {
				num = (Number) rs.getObject(1);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close(rs, ps, conn);
		}
		System.out.println(num);
		return num;
	}
	
	public static void executeSQL(String sql) {
		Connection conn = null;
		PreparedStatement ps = null;
		
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			close(null, ps, conn);
		}
	}

	public static void save(Object obj) {
		Connection conn = null;
		PreparedStatement ps = null;
		
		String sql = getInsertSQL(obj);
		System.out.println(sql);
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			close(null, ps, conn);
		}
	}
	
	public static void update(Object obj) {
		Connection conn = null;
		PreparedStatement ps = null;
		
		String sql = getUpdateSQL(obj);
		try {
			conn = getConn();
			
			System.out.println(sql);
			ps = conn.prepareStatement(sql);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			close(null, ps, conn);
		}
	}

	public static void delete(Object obj) {
		Connection conn = null;
		PreparedStatement ps = null;
		
		String sql = getDeleteSQL(obj);
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			close(null, ps, conn);
		}
	}
	
	private static String getInsertSQL(Object obj) {
		Class clazz = obj.getClass();
		
		String table_name = clazz.getSimpleName().toLowerCase();
		String columns = "";
		String values = "";
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		try {
			Method[] methods = clazz.getDeclaredMethods();
			for (Method method : methods) {
				String method_name = method.getName();
				if(method_name.startsWith("get")) {
					String elementName = method_name.substring(3, 4).toLowerCase() + method_name.substring(4);
					Object value = method.invoke(obj);
					if(value != null) {
						columns += elementName + ",";
						if(method.getReturnType().getSuperclass().getSimpleName().equals("Number")
								|| method.getReturnType().getSimpleName().equals("Number")) {
							values += value + ",";
						} else if(method.getReturnType().getSimpleName().equals("Date")) {
							//values += "to_date('"+sdf.format(value)+"','yyyy-MM-dd'),";
							values+= "'"+sdf.format(value)+"',";
						} else {
							values += "'" + value + "',";
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		columns = columns.substring(0, columns.length() - 1);
		values = values.substring(0, values.length() - 1);
		
		String sql = "insert into "+table_name+"("+columns+") values("+values+")";		
		return sql;
	}
	
	public static String getUpdateSQL(Object obj) {
		Class clazz = obj.getClass();
		
		String table_name = clazz.getSimpleName().toLowerCase();
		String keyValues = "";
		Object pk = "";
		
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			
			Method[] methods = clazz.getDeclaredMethods();
			for (Method method : methods) {
				String method_name = method.getName();
				if(method_name.startsWith("get")) {
					String key = method_name.substring(3, 4).toLowerCase() + method_name.substring(4);
					Object value = method.invoke(obj);
					
					if(value != null) {
						if(key.equals("id")) {
							pk = value;
						} else {
							if(method.getReturnType().getSuperclass().getSimpleName().equals("Number")
									||method.getReturnType().getSimpleName().equals("Number")) {
								keyValues += key + "=" + value + ",";
							} else if(method.getReturnType().getSimpleName().equals("Date")) {
								//keyValues += key + "=to_date("+sdf.format(value)+", 'yyyy-MM-dd'),";
								keyValues+= key+ "="+"'"+sdf.format(value)+"',";
							} else {
								keyValues += key + "='" + value + "',";
							}
						}
					}
				}
			}
			
			keyValues = keyValues.substring(0, keyValues.length() - 1);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		String sql = "update "+table_name+" set "+keyValues+" where id="+pk;
		System.out.println(sql);
		return sql;
	}
	
	private static String getDeleteSQL(Object obj) {
		Class clazz = obj.getClass();
		
		String table_name = clazz.getSimpleName().toLowerCase();
		Object pk = "";
		try {
			Method method = clazz.getMethod("getId");
			pk = method.invoke(obj);
		} catch (Exception e) {
			e.printStackTrace();
		}
		String sql = "delete from "+table_name+" where id=" + pk;
		return sql;
	}
	
	private static void close(ResultSet rs, Statement stmt, Connection conn) {
		if(rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		if(stmt != null) {
			try {
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		if(conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
}
