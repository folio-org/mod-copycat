CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.update_cpct_profile(
	input_jsonb jsonb)
    RETURNS jsonb
    LANGUAGE 'plpgsql'
    COST 100
    VOLATILE PARALLEL UNSAFE
AS $BODY$
begin
  IF(input_jsonb::jsonb->'allowedCreateJobProfileIds' IS NULL) THEN
	  input_jsonb = jsonb_set(input_jsonb,'{allowedCreateJobProfileIds}',jsonb_build_array(input_jsonb::jsonb->'createJobProfileId'));
	END IF;
	IF(input_jsonb::jsonb->'allowedUpdateJobProfileIds' IS NULL) THEN
  	input_jsonb = jsonb_set(input_jsonb,'{allowedUpdateJobProfileIds}',jsonb_build_array(input_jsonb::jsonb->'updateJobProfileId'));
  END IF;

	return input_jsonb;
end
$BODY$;

UPDATE ${myuniversity}_${mymodule}.profile
  SET jsonb=${myuniversity}_${mymodule}.update_cpct_profile(jsonb);

DROP FUNCTION IF EXISTS ${myuniversity}_${mymodule}.update_cpct_profile(input_jsonb jsonb);
