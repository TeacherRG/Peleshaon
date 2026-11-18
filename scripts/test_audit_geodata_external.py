import importlib.util
import pathlib
import unittest

spec=importlib.util.spec_from_file_location("auditor",pathlib.Path(__file__).with_name("audit_geodata_external.py"))
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class GeoAuditTest(unittest.TestCase):
    def test_distance_identity(self):
        self.assertAlmostEqual(module.geo_distance_km(49.0,32.0,49.0,32.0),0.0)
        self.assertTrue(100 < module.geo_distance_km(49.0,32.0,50.0,32.0) < 120)
    def test_romanized_official_names(self):
        self.assertEqual(module.romanize("Кам’янка"),"kamianka")
        self.assertEqual(module.romanize("Знам’янка"),"znamianka")
        self.assertGreater(module.score_names("Kamianka",module.romanize("Кам’янка")),.9)
    def test_nearby_region_and_name_required(self):
        city={"id":"ua:71:kamianka","en":"Kamianka","uk":"Кам’янка","ru":"Кам’янка","lat":49.0318,"lon":32.10396}
        reference={"71":[(49.032,32.104,"Кам’янка","UA71ABC","code"),
                         (49.033,32.105,"Інше місто","UA71XYZ","code")],
                   "35":[(49.032,32.104,"Кам’янка","UA35WRONG","code")]}
        status,candidate,_=module.find_ua(city,reference)
        self.assertEqual(status,"INDEPENDENT_MATCH")
        self.assertEqual(candidate[3],"UA71ABC")
    def test_wrong_name_is_not_verified(self):
        city={"id":"ua:71:kamianka","en":"Wrong","uk":"","ru":"Wrong","lat":49.0318,"lon":32.10396}
        status,_,_=module.find_ua(city,{"71":[(49.032,32.104,"Кам’янка","UA71ABC","code")]})
        self.assertNotEqual(status,"INDEPENDENT_MATCH")
if __name__=="__main__": unittest.main()
