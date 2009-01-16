package chibi.gemmaanalysis;

import java.util.Collection;

import ubic.gemma.model.genome.Gene;
import ubic.gemma.model.genome.PhysicalLocation;
import ubic.gemma.model.genome.PhysicalLocationService;
import ubic.gemma.model.genome.ProbeAlignedRegion;
import ubic.gemma.model.genome.Taxon;
import ubic.gemma.model.genome.TaxonService;
import ubic.gemma.model.genome.gene.GeneService;
import ubic.gemma.util.AbstractSpringAwareCLI;

/**
 * Collect statistics about how far PARs are from known genes.
 * 
 * @author paul
 * @version $Id$
 */
public class PARMapper extends AbstractSpringAwareCLI {

    GeneService parService;
    TaxonService taxonService;
    PhysicalLocationService physicalLocationservice;

    @Override
    protected void buildOptions() {
    }

    @Override
    protected Exception doWork( String[] args ) {
        Exception exc = processCommandLine( "PARMapper", args );
        if ( exc != null ) return exc;

        this.taxonService = ( TaxonService ) this.getBean( "taxonService" );
        this.parService = ( GeneService ) this.getBean( "geneService" );
        this.physicalLocationservice = ( PhysicalLocationService ) this.getBean( "physicalLocationService" );

        /*
         * FIXME don't hard-code this.
         */
        Taxon taxon = taxonService.findByCommonName( "mouse" );
        if ( taxon == null ) {
            throw new IllegalArgumentException();
        }
        Collection<ProbeAlignedRegion> pars = parService.loadProbeAlignedRegions( taxon );

        log.info( pars.size() + " PARS" );
        System.out.println( "ParID\tParName\tChrom\tNuc\tGeneId\tGeneSymbol" );
        for ( ProbeAlignedRegion par : pars ) {
            parService.thaw( par );

            PhysicalLocation loc = par.getPhysicalLocation();

            physicalLocationservice.thaw( loc );

            if ( loc == null ) continue;
            loc.setStrand( null );
            Collection<Gene> nearest = parService.findNearest( loc );
            if ( !nearest.isEmpty() ) {
                for ( Gene gene : nearest ) {

                    System.out.println( par.getId() + "\t" + par.getName() + "\t" + loc.getChromosome().getName()
                            + "\t" + loc.getNucleotide() + "\t" + gene.getId() + "\t" + gene.getOfficialSymbol() );

                }
            }
        }
        return null;
    }

    /**
     * @param args
     */
    public static void main( String[] args ) {
        PARMapper p = new PARMapper();
        Exception e = p.doWork( args );
        if ( e != null ) {
            throw new RuntimeException( e );
        }

    }

}