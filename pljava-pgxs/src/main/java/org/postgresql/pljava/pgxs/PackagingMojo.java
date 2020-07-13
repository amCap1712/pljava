package org.postgresql.pljava.pgxs;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "packaging", defaultPhase = LifecyclePhase.INITIALIZE)
public class PackagingMojo extends AbstractMojo
{
	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	MavenProject project;

	@Override
	public void execute ()
	{
		String PGSQL_VER_CLASSIFIER = "PGSQL_VER_CLASSIFIER";
		String versionCommand = "--version";
		String pgConfigCommand = project.getProperties()
			                         .getProperty("pgsql.pgconfig");
		try
		{
			String propertyValue = PGXSUtils.getPgConfigProperty(
				pgConfigCommand, versionCommand)
				                       .replaceAll(
					                       "devel.*|alpha.*|beta.*|rc.*$",
					                       "\\.99")
				                       .replaceAll(
					                       "^[^\\d]*+(\\d++)\\.(\\d++)(?:\\.(\\d++))?.*$",
					                       "pg$1.$2");

			project.getProperties()
				.setProperty(PGSQL_VER_CLASSIFIER, propertyValue);
			getLog().info("PGXS Property Set: "
				              + PGSQL_VER_CLASSIFIER + "=" + propertyValue);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}
}
