package pl.peterwolf.cinewolf.api.v2;

import pl.peterwolf.cinewolf.montage.v2.MontageStyleProfile;

import java.util.List;
import java.util.Optional;

/** Supplies reusable montage style profiles for planning. */
public interface MontageProfileProvider {
    String providerId();

    String displayName();

    List<MontageStyleProfile> profiles();

    default Optional<MontageStyleProfile> find(String profileId) {
        return profiles().stream().filter(profile -> profile.id().equals(profileId)).findFirst();
    }
}
