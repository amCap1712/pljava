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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Mojo(name = "properties", defaultPhase = LifecyclePhase.INITIALIZE)
public class PropertiesMojo extends AbstractMojo
{
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	MavenProject project;

	@Parameter(defaultValue = "${project.build.directory}", required = true)
	private File targetDirectory;

	private Map<String, String> pgConfigProperties = Map.of("PGSQL_BINDIR",
			"--bindir", "PGSQL_LIBDIR", "--libdir", "PGSQL_INCLUDEDIR",
			"--includedir", "PGSQL_INCLUDEDIR-SERVER", "--includedir-server",
			"PGSQL_VER_CLASSIFIER", "--version");

	@Override
	public void execute ()
	{
		try
		{
			StringBuilder propertiesDataBuilder = new StringBuilder();
			propertiesDataBuilder.append("# ")
					.append(LocalDateTime.now()
							.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
					.append(System.getProperty("line.separator"));

			for (Map.Entry<String, String> entry : pgConfigProperties.entrySet())
			{
				String propertyValue = getPgConfigProperty(entry.getValue());
				getLog().warn(propertyValue);
				if (entry.getKey().equalsIgnoreCase("PGSQL_VER_CLASSIFIER"))
					propertyValue = propertyValue
							.replaceAll("devel.*|alpha.*|beta.*|rc.*$", "\\.99")
							.replaceAll("^[^\\d]*+(\\d++)\\.(\\d++)(?:\\." +
									"(\\d++))?.*$", "pg$1.$2");

				project.getProperties().setProperty(entry.getKey(),
						propertyValue);

				getLog().debug("PGXS Property Set: "
						+ entry.getKey() + "=" + propertyValue + "\n");

				propertiesDataBuilder.append(entry.getKey())
						.append("=")
						.append(propertyValue)
						.append("\n");
			}

			File propertiesFile = new File(targetDirectory.getAbsolutePath() +
					File.separator + "pgxs.properties");
			getLog().info(propertiesFile.getAbsolutePath());
			if (!propertiesFile.exists())
				propertiesFile.createNewFile();
			FileWriter writer = new FileWriter(propertiesFile);
			writer.write(propertiesDataBuilder.toString());
			writer.flush();
			writer.close();

		} catch (Exception e)
		{
			getLog().error(e);
		}
	}

	private String getPgConfigProperty (String pgConfigArgument)
			throws IOException, InterruptedException
	{
		Process process = new ProcessBuilder("pg_config",
				pgConfigArgument).start();
		getLog().warn(process.info().toString());
		BufferedInputStream inputStream =
				new BufferedInputStream(process.getInputStream());
		byte[] bytes = inputStream.readAllBytes();
		process.waitFor();
		return new String(bytes).trim();
	}
}
