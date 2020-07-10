/*
 * Copyright (c) 2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Kartik Ohri
 */
package org.postgresql;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "properties", defaultPhase = LifecyclePhase.INITIALIZE)
public class PropertiesMojo extends AbstractMojo
{
	static Pattern mustBeQuotedForC = Pattern.compile(
			"([\"\\\\]|(?<=\\?)\\?(?=[=(/)'<!>-]))|" +  // (just insert backslash)
					"([\\a\b\\f\\n\\r\\t\\x0B])|" +     // (use specific escapes)
					"(\\p{Cc}((?=\\p{XDigit}))?)"
			// use hex, note whether an XDigit follows
	);

	@Parameter(defaultValue = "${session}", required = true, readonly = true)
	MavenSession session;

	@Parameter(defaultValue = "${project.build.directory}", required = true)
	private File targetDirectory;

	private Map<String, String> pgConfigProperties = Map.of("PGSQL_BINDIR",
			"--bindir", "PGSQL_LIBDIR", "--libdir", "PGSQL_INCLUDEDIR",
			"--includedir", "PGSQL_INCLUDEDIR-SERVER", "--includedir-server",
			"PGSQL_VER_CLASSIFIER", "--version");

	private String pgConfigCommand;

	public static String getStringFromBytes (byte[] bytes)
			throws CharacterCodingException
	{
		return Charset.defaultCharset().newDecoder()
				.decode(ByteBuffer.wrap(bytes)).toString();
	}

	@Override
	public void execute ()
	{
		try
		{
			setLibJvmDefault();
			pgConfigCommand = session.getCurrentProject()
					.getProperties().getProperty("pgsql.pgconfig");

			for (Map.Entry<String, String> entry : pgConfigProperties.entrySet())
			{
				String propertyValue = getPgConfigProperty(entry.getValue());
				if (entry.getKey().equalsIgnoreCase("PGSQL_VER_CLASSIFIER"))
				{
					propertyValue = propertyValue
							.replaceAll("devel.*|alpha.*|beta.*|rc.*$", "\\.99")
							.replaceAll("^[^\\d]*+(\\d++)\\.(\\d++)(?:\\." +
									"(\\d++))?.*$", "pg$1.$2");

					String finalPropertyValue = propertyValue;
					session.getAllProjects()
							.stream()
							.filter(project -> project.getArtifactId()
									.contains("packaging"))
							.forEach(project -> project.getProperties()
									.setProperty(entry.getKey(),
											finalPropertyValue));
				} else
				{
					session.getCurrentProject().getProperties()
							.setProperty(entry.getKey(), propertyValue);
				}

				getLog().info("PGXS Property Set: "
						+ entry.getKey() + "=" + propertyValue);
			}
		} catch (Exception e)
		{
			getLog().error(e);
		}
	}

	private String getPgConfigProperty (String pgConfigArgument)
			throws IOException, InterruptedException
	{
		ProcessBuilder processBuilder = new ProcessBuilder(pgConfigCommand,
				pgConfigArgument);
		processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
		Process process = processBuilder.start();
		process.getOutputStream().close();
		byte[] bytes = process.getInputStream().readAllBytes();
		process.waitFor();
		if (process.exitValue() != 0)
			throw new InterruptedException("pg_config process failed and " +
					"exited with " + process.exitValue());
		String pgConfigOutput = getStringFromBytes(bytes);
		return pgConfigOutput.substring(0, pgConfigOutput.length() - 1);
	}

	private void setLibJvmDefault ()
	{
		String jvmdflt = System.getProperty("pljava.libjvmdefault");
		if (null != jvmdflt)
		{
			String jvmdfltQuoted = quoteStringForC(jvmdflt);
			session.getCurrentProject().getProperties()
					.setProperty("pljava.qlibjvmdefault", jvmdfltQuoted);
		}
	}

	/* This is the function that will return s wrapped in double quotes and with
	   internal characters escaped where appropriate using the C conventions.
	 */
	private String quoteStringForC (String s)
	{
		Matcher m = mustBeQuotedForC.matcher(s);
		StringBuffer b = new StringBuffer();
		while (m.find())
		{
			if (-1 != m.start(1)) // things that just need a backslash
				m.appendReplacement(b, "\\\\$1");
			else if (-1 != m.start(2)) // things with specific escapes
			{
				char ec = 0;
				switch (m.group(2)) // switch/case uses ===
				{
					case "\u0007":
						ec = 'a';
						break;
					case "\b":
						ec = 'b';
						break;
					case "\f":
						ec = 'f';
						break;
					case "\n":
						ec = 'n';
						break;
					case "\r":
						ec = 'r';
						break;
					case "\t":
						ec = 't';
						break;
					case "\u000B":
						ec = 'v';
						break;
				}
				m.appendReplacement(b, "\\\\" + ec);
			} else // it's group 3, use hex escaping
			{
				m.appendReplacement(b,
						"\\\\x" + Integer.toHexString(
								m.group(3).codePointAt(0)) +
								(-1 == m.start(4) ? "" : "\"\"")); // XDigit follows?
			}
		}
		return "\"" + m.appendTail(b) + "\"";
	}
}
