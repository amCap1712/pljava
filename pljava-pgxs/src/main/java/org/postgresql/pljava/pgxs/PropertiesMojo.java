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
package org.postgresql.pljava.pgxs;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.Map;

@Mojo(name = "properties", defaultPhase = LifecyclePhase.INITIALIZE)
public class PropertiesMojo extends AbstractMojo
{
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	MavenProject project;

	private Map<String, String> pgConfigProperties = Map.of("PGSQL_BINDIR",
		"--bindir", "PGSQL_LIBDIR", "--libdir", "PGSQL_INCLUDEDIR",
		"--includedir", "PGSQL_INCLUDEDIR-SERVER", "--includedir-server",
		"PGSQL_PKGLIBDIR", "--pkglibdir");

	private String pgConfigCommand;

	@Override
	public void execute ()
	{
		try
		{
			setLibJvmDefault();
			pgConfigCommand = project.getProperties()
				                  .getProperty("pgsql.pgconfig");

			for (Map.Entry<String, String> entry : pgConfigProperties.entrySet())
			{
				String propertyValue = PGXSUtils.getPgConfigProperty(
					pgConfigCommand,
					entry.getValue());

				project.getProperties()
					.setProperty(entry.getKey(), propertyValue);

				getLog().info("PGXS Property Set: "
					              + entry.getKey() + "=" + propertyValue);
			}
		}
		catch (Exception e)
		{
			getLog().error(e);
		}
	}

	private void setLibJvmDefault ()
	{
		String jvmdflt = System.getProperty("pljava.libjvmdefault");
		if (null != jvmdflt)
		{
			String jvmdfltQuoted = PGXSUtils.quoteStringForC(jvmdflt);
			project.getProperties()
				.setProperty("pljava.qlibjvmdefault", jvmdfltQuoted);
		}
	}
}
