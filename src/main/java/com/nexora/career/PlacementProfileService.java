package com.nexora.career;

import org.springframework.stereotype.Service;

@Service
public class PlacementProfileService {
    private final PlacementProfileRepository repository;
    public PlacementProfileService(PlacementProfileRepository repository) { this.repository = repository; }
    public PlacementProfile get(String reg) { return repository.find(reg).orElse(defaultProfile(reg)); }
    public void save(PlacementProfile profile) { repository.save(profile); }
    private PlacementProfile defaultProfile(String reg) {
        return new PlacementProfile(reg,null,0,null,null,0,0,null,null,0,0,0,0,0,0,0,false,false,false,null,null,null,null,null,"Basic Readiness","Satisfactory",0.0,0.0,false,false,0,null,null);
    }
}
