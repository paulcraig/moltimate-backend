package org.moltimate.moltimatebackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.Group;
import org.biojava.nbio.structure.Structure;
import org.moltimate.moltimatebackend.util.StructureUtils;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Motif {

    @Id
    @NotNull
    private String pdbId;

    @NotNull
    private String ecNumber;

    @NotNull
    @ElementCollection
    private Map<String, ResidueQuerySet> selectionQueries;

    @Valid
    @NotNull
    @ElementCollection
    private List<Residue> activeSiteResidues;

    private Residue getResidueByName(String residueName) {
        for (Residue res : activeSiteResidues) {
            if (res.getIdentifier().equals(residueName)) {
                return res;
            }
        }
        return null;
    }

    public Map<Residue, List<Group>> runQueries(Structure pdb) {
        return runQueries(pdb, 1d);
    }

    /**
     * Apply motif to pdb structure
     *
     * @param pdb:             structure of pdb to run alignment on
     * @param precisionFactor: precision factor of search
     * @return a list of amino acids that match the set of queries
     */
    public Map<Residue, List<Group>> runQueries(Structure pdb, double precisionFactor) {
        //TODO: Refactor
        Map<Residue, List<Group>> residues = new HashMap<>();
        selectionQueries.entrySet()
                        .forEach(entry -> {
                            String residueName = entry.getKey();
                            Residue residueValue = getResidueByName(residueName);
                            HashSet<Atom> atoms = new HashSet<>();
                            HashMap<Group, Integer> groupCount = new HashMap<>();
                            selectionQueries.get(residueName)
                                            .getSelections()
                                            .forEach(query -> {

                                                List<Atom> atomsFound = StructureUtils.runQuery(
                                                        pdb,
                                                        query.getAtomType1(),
                                                        query.getAtomType2(),
                                                        query.getResidueName1(),
                                                        query.getResidueName2(),
                                                        query.getDistance(),
                                                        precisionFactor
                                                );
                                                atoms.addAll(atomsFound);
                                                Set<Group> groupsMatchingQuery = new HashSet<>();
                                                HashSet<String> foundNames = new HashSet<>();
                                                atomsFound.forEach(atom -> {
                                                    if (!foundNames.contains(atom.getGroup()
                                                                                 .toString())) {
                                                        foundNames.add(atom.getGroup()
                                                                           .toString());
                                                        groupsMatchingQuery.add(atom.getGroup());
                                                    }
                                                });
                                                groupsMatchingQuery.forEach(residue -> groupCount.merge(
                                                        residue,
                                                        1,
                                                        (a, b) -> a + b
                                                ));
                                            });
                            List<Group> candidateGroups = groupCount.keySet()
                                                                    .stream()
                                                                    .filter(group -> groupCount.get(group) == selectionQueries
                                                                            .get(
                                                                                    residueName)
                                                                            .getSelections()
                                                                            .size())

                                                                    .collect(Collectors.toList());
                            candidateGroups.forEach(candidate -> {
                                residues.computeIfAbsent(residueValue, k -> new ArrayList<>());
                                residues.get(residueValue)
                                        .add(candidate);
                            });
                        });
        return residues;
    }
}
